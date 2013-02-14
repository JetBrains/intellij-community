/*
* Copyright 2000-2013 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#include "stdafx.h"
#include "WinLauncher.h"

typedef JNIIMPORT jint (JNICALL *JNI_createJavaVM)(JavaVM **pvm, JNIEnv **env, void *args);

HINSTANCE hInst;								// current instance
char jvmPath[_MAX_PATH];
JavaVMOption* vmOptions = NULL;
int vmOptionCount = 0;
bool bServerJVM = false;
HMODULE hJVM = NULL;
JNI_createJavaVM pCreateJavaVM = NULL;
JavaVM* jvm = NULL;
JNIEnv* env = NULL;

bool IsValidJRE(char* path)
{
	char dllPath[_MAX_PATH];
	strcpy_s(dllPath, path);
	strcat_s(dllPath, "\\bin\\client\\jvm.dll");
	return GetFileAttributesA(dllPath) != INVALID_FILE_ATTRIBUTES;
}

bool FindValidJVM(char* path)
{
	if (IsValidJRE(path))
	{
		strcpy_s(jvmPath, _MAX_PATH-1, path);
		return true;
	}
	char jrePath[_MAX_PATH];
	strcpy_s(jrePath, path);
	if (jrePath[strlen(jrePath)-1] != '\\')
	{
		strcat_s(jrePath, "\\");
	}
	strcat_s(jrePath, _MAX_PATH-1, "jre");
	if (IsValidJRE(jrePath))
	{
		strcpy_s(jvmPath, jrePath);
		return true;
	}
	return false;
}

bool LocateJVM()
{
	char envVarName[_MAX_PATH];
	if (LoadStringA(hInst, IDS_JDK_ENV_VAR, envVarName, _MAX_PATH-1))
	{
		char envVarValue[_MAX_PATH];
		if (GetEnvironmentVariableA(envVarName, envVarValue, _MAX_PATH-1))
		{
			if (FindValidJVM(envVarValue))
			{
				return true;
			}
			else
			{
				char buf[_MAX_PATH];
				sprintf_s(buf, "The environment variable %s (with the value of %s) does not point to a valid JVM installation",
					envVarValue, jvmPath);
				MessageBoxA(NULL, buf, "Error Launching IntelliJ Platform", MB_OK);
				return false;
			}
		}
	}
	return false;
}

void TrimLine(char* line)
{
	char *p = line + strlen(line) - 1;
	if (p >= line && *p == '\n')
	{
		*p-- = '\0';
	}
	while(p >= line && (*p == ' ' || *p == '\t'))
	{
		*p-- = 0;
	}
}

bool LoadVMOptionsFile(const TCHAR* path, std::vector<std::string>& vmOptionLines)
{
	FILE *f = _tfopen(path, _T("rt"));
	if (!f) return false;
	
	char line[_MAX_PATH];
	while(fgets(line, _MAX_PATH-1, f))
	{
		TrimLine(line);
		if (strcmp(line, "-server") == 0)
		{
			bServerJVM = true;
		}
		else if (strlen(line) > 0)
		{
			vmOptionLines.push_back(line);
		}
	}
	fclose(f);

	return true;
}

std::string FindToolsJar()
{
	std::string toolsJarPath = jvmPath;
	size_t lastSlash = toolsJarPath.rfind('\\');
	if (lastSlash != std::string::npos)
	{
		toolsJarPath = toolsJarPath.substr(0, lastSlash+1);
		toolsJarPath += "lib\\tools.jar";
		if (GetFileAttributesA(toolsJarPath.c_str()) != INVALID_FILE_ATTRIBUTES)
		{
			return toolsJarPath;
		}
	}
	return "";
}

std::string BuildClassPath()
{
	char libDir[_MAX_PATH];
	GetCurrentDirectoryA(_MAX_PATH-1, libDir);
	char* lastSlash = strrchr(libDir, '\\');
	strcpy(lastSlash+1, "lib\\");
	if (GetFileAttributesA(libDir) == INVALID_FILE_ATTRIBUTES)
	{
		return "";
	}

	char classpathLibs[_MAX_PATH];
	std::string result;
	LoadStringA(hInst, IDS_CLASSPATH_LIBS, classpathLibs, _MAX_PATH-1);
	char *pJarName = classpathLibs;
	while(pJarName)
	{
		char *pNextJarName = strchr(pJarName, ';');
		if (pNextJarName)
		{
			*pNextJarName++ = '\0';
		}
		if (result.size() > 0)
		{
			result += ";";
		}
		result += libDir;
		result += pJarName;
		pJarName = pNextJarName;
	}

	std::string toolsJar = FindToolsJar();
	if (toolsJar.size() > 0)
	{
		result += ";";
		result += toolsJar;
	}

	return result;
}

bool LoadVMOptions()
{
	TCHAR optionsFileName[_MAX_PATH];
	if (LoadString(hInst, IDS_VM_OPTIONS_PATH, optionsFileName, _MAX_PATH-1))
	{
		TCHAR optionsFileNameExpanded[_MAX_PATH];
		ExpandEnvironmentStrings(optionsFileName, optionsFileNameExpanded, _MAX_PATH-1);
		
		std::vector<std::string> vmOptionLines;
		if (LoadVMOptionsFile(optionsFileNameExpanded, vmOptionLines))
		{
			std::string classPath = BuildClassPath();
			if (classPath.size() == 0) return false;
			std::string classPathArg("-Djava.class.path=");
			classPathArg += classPath;
			vmOptionLines.push_back(classPathArg);

			vmOptionCount = vmOptionLines.size();
			vmOptions = (JavaVMOption*) malloc(vmOptionCount * sizeof(JavaVMOption));
			for(int i=0; i<vmOptionLines.size(); i++)
			{
				vmOptions[i].optionString = _strdup(vmOptionLines[i].c_str());
				vmOptions[i].extraInfo = 0;
			}

			return true;
		}
	}
	MessageBox(NULL, _T("Cannot find VM options file"), _T("Error launching IntelliJ Platform"), MB_OK);
	return false;
}

bool LoadJVMLibrary()
{
	std::string dllName(jvmPath);
	if (bServerJVM)
	{
		dllName += "\\bin\\server\\jvm.dll";
	}
	else
	{
		dllName += "\\bin\\client\\jvm.dll";
	}
	hJVM = LoadLibraryA(dllName.c_str());
	if (hJVM)
	{
		pCreateJavaVM = (JNI_createJavaVM) GetProcAddress(hJVM, "JNI_CreateJavaVM");
	}
	if (!pCreateJavaVM)
	{
		TCHAR buf[_MAX_PATH];
		_stprintf_s(buf, _MAX_PATH-1, _T("Failed to load JVM DLL %s"), dllName);
		MessageBox(NULL, buf, _T("Error Launching IntelliJ Platform"), MB_OK);
		return false;
	}
	return true;
}

bool CreateJVM()
{
	JavaVMInitArgs initArgs;
	initArgs.version = JNI_VERSION_1_2;
	initArgs.options = vmOptions;
	initArgs.nOptions = vmOptionCount;
	initArgs.ignoreUnrecognized = JNI_FALSE;

	int result = pCreateJavaVM(&jvm, &env, &initArgs);

	for(int i=0; i<vmOptionCount; i++)
	{
		free(vmOptions[i].optionString);
	}
	free(vmOptions);
	vmOptions = NULL;

	if (result != JNI_OK)
	{
		MessageBox(NULL, _T("Failed to create JVM"), _T("Error launching IntelliJ Platform"), MB_OK);
	}

	return result == JNI_OK;
}

bool RunMainClass()
{
	char mainClassName[_MAX_PATH];
	if (!LoadStringA(hInst, IDS_MAIN_CLASS, mainClassName, _MAX_PATH-1)) return false;
	jclass mainClass = env->FindClass(mainClassName);
	if (!mainClass)
	{
		char buf[_MAX_PATH];
		sprintf(buf, "Could not find main class %s", mainClassName);
		MessageBoxA(NULL, buf, "Error Launching IntelliJ Platform", MB_OK);
		return false;
	}

	jclass stringClass = env->FindClass("java/lang/String");
	jobjectArray args = env->NewObjectArray(0, stringClass, NULL);
	jmethodID mainMethod = env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
	if (!mainClass)
	{
		MessageBoxA(NULL, "Could not find main method", "Error Launching IntelliJ Platform", MB_OK);
		return false;
	}

	env->CallStaticVoidMethod(mainClass, mainMethod, args);

	return true;
}

int APIENTRY _tWinMain(HINSTANCE hInstance,
                     HINSTANCE hPrevInstance,
                     LPTSTR    lpCmdLine,
                     int       nCmdShow)
{
	UNREFERENCED_PARAMETER(hPrevInstance);
	UNREFERENCED_PARAMETER(lpCmdLine);

	hInst = hInstance;

	if (!LocateJVM()) return 1;
	if (!LoadVMOptions()) return 1;
	if (!LoadJVMLibrary()) return 1;
	if (!CreateJVM()) return 1;
	if (!RunMainClass()) return 1;

	jvm->DestroyJavaVM();

	return 0;
}



