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
volatile bool terminating = false;

HANDLE hFileMapping;
HANDLE hEvent;
HANDLE hSingleInstanceWatcherThread;
const int FILE_MAPPING_SIZE = 16000;

#ifdef _M_X64
bool need64BitJRE = true;
#else
bool need64BitJRE = false;
#endif

std::string LoadStdString(int id)
{
	wchar_t *buf = NULL;
	int len = LoadStringW(hInst, id, reinterpret_cast<LPWSTR>(&buf), 0);
	if (len) 
	{
		int cbANSI = WideCharToMultiByte(CP_ACP, 0, buf, len, NULL, 0, NULL, NULL);
		char* ansiBuf = new char[cbANSI];
		WideCharToMultiByte(CP_ACP, 0, buf, len, ansiBuf, cbANSI, NULL, NULL);
		std::string result(ansiBuf, cbANSI);
		delete[] ansiBuf;
		return result;
	}
	return std::string();
}

bool FileExists(const std::string& path)
{
	return GetFileAttributesA(path.c_str()) != INVALID_FILE_ATTRIBUTES;
}

bool IsValidJRE(const char* path)
{
	std::string dllPath(path);
	if (dllPath[dllPath.size()-1] != '\\')
	{
		dllPath += "\\";
	}
	return FileExists(dllPath + "bin\\server\\jvm.dll") || FileExists(dllPath + "bin\\client\\jvm.dll");
}

bool Is64BitJRE(const char* path)
{
	std::string cfgPath(path);
	cfgPath += "\\lib\\amd64\\jvm.cfg";
	return FileExists(cfgPath);
}

bool FindValidJVM(const char* path)
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

std::string GetAdjacentDir(const char* suffix)
{
	char libDir[_MAX_PATH];
	GetModuleFileNameA(NULL, libDir, _MAX_PATH-1);
	char* lastSlash = strrchr(libDir, '\\');
	if (!lastSlash) return "";
	*lastSlash = '\0';
	lastSlash = strrchr(libDir, '\\');
	if (!lastSlash) return "";
	strcpy(lastSlash+1, suffix);
	strcat_s(libDir, "\\");
	return std::string(libDir);
}

bool FindJVMInEnvVar(const char* envVarName, bool& result)
{
	char envVarValue[_MAX_PATH];
	if (GetEnvironmentVariableA(envVarName, envVarValue, _MAX_PATH-1))
	{
		if (FindValidJVM(envVarValue))
		{
			if (Is64BitJRE(jvmPath) != need64BitJRE) return false;
			result = true;
		}
		else
		{
			char buf[_MAX_PATH];
			sprintf_s(buf, "The environment variable %s (with the value of %s) does not point to a valid JVM installation.",
				envVarName, envVarValue);
			MessageBoxA(NULL, buf, "Error Launching IntelliJ Platform", MB_OK);
			result = false;
		}
		return true;
	}
	return false;
}

bool FindJVMInRegistryKey(const char* key, bool wow64_32)
{
	HKEY hKey;
	int flags = KEY_READ;
	if (wow64_32) flags |= KEY_WOW64_32KEY;
	if (RegOpenKeyExA(HKEY_LOCAL_MACHINE, key, 0, KEY_READ, &hKey) != ERROR_SUCCESS) return false;
	char javaHome[_MAX_PATH];
	DWORD javaHomeSize = _MAX_PATH-1;
	bool success = false;
	if (RegQueryValueExA(hKey, "JavaHome", NULL, NULL, (LPBYTE) javaHome, &javaHomeSize) == ERROR_SUCCESS)
	{
		success = FindValidJVM(javaHome);
	}
	RegCloseKey(hKey);
	return success;
}

bool FindJVMInRegistryWithVersion(const char* version, bool wow64_32)
{
	const char* keyName = LoadStdString(IDS_JDK_ONLY) == std::string("true") 
		? "Java Development Kit"
		: "Java Runtime Environment";

	char buf[_MAX_PATH];
	sprintf_s(buf, "Software\\JavaSoft\\%s\\%s", keyName, version);
	return FindJVMInRegistryKey(buf, wow64_32);
}

bool FindJVMInRegistry()
{
#ifndef _M_X64
	if (FindJVMInRegistryWithVersion("1.7", true))
		return true;
	if (FindJVMInRegistryWithVersion("1.6", true))
		return true;
#endif

	if (FindJVMInRegistryWithVersion("1.7", false))
		return true;
	if (FindJVMInRegistryWithVersion("1.6", false))
		return true;
	return false;
}

bool LocateJVM()
{
	bool result;
	if (FindJVMInEnvVar(LoadStdString(IDS_JDK_ENV_VAR).c_str(), result))
	{
		return result;
	}

	std::string jreDir = GetAdjacentDir("jre");
	if (FindValidJVM(jreDir.c_str()) && Is64BitJRE(jvmPath) == need64BitJRE)
	{
		return true;
	}

	if (FindJVMInRegistry())
	{
		return true;
	}

	if (FindJVMInEnvVar("JAVA_HOME", result))
	{
		return result;
	}

	MessageBoxA(NULL, "No JVM installation found. Please reinstall the product or install a JDK.", "Error Launching IntelliJ Platform", MB_OK);
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
		*p-- = '\0';
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
		if (line[0] == '#') continue;
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
		toolsJarPath = toolsJarPath.substr(0, lastSlash+1) + "lib\\tools.jar";
		if (FileExists(toolsJarPath))
		{
			return toolsJarPath;
		}
	}
	return "";
}

std::string CollectLibJars(const std::string& jarList)
{
	std::string libDir = GetAdjacentDir("lib");
	if (libDir.size() == 0 || !FileExists(libDir))
	{
		return "";
	}

	std::string result;
	int pos = 0;
	while(pos < jarList.size())
	{
		int delimiterPos = jarList.find(';', pos);
		if (delimiterPos == std::string::npos)
		{
			delimiterPos = jarList.size();
		}
		if (result.size() > 0)
		{
			result += ";";
		}
		result += libDir;
		result += jarList.substr(pos, delimiterPos-pos);
		pos = delimiterPos+1;
	}
	return result;
}

std::string BuildClassPath()
{
	std::string classpathLibs = LoadStdString(IDS_CLASSPATH_LIBS);
	std::string result = CollectLibJars(classpathLibs);

	std::string toolsJar = FindToolsJar();
	if (toolsJar.size() > 0)
	{
		result += ";";
		result += toolsJar;
	}

	return result;
}

bool AddClassPathOptions(std::vector<std::string>& vmOptionLines)
{
	std::string classPath = BuildClassPath();
	if (classPath.size() == 0) return false;
	vmOptionLines.push_back(std::string("-Djava.class.path=") + classPath);

	std::string bootClassPathLibs = LoadStdString(IDS_BOOTCLASSPATH_LIBS);
	std::string bootClassPath = CollectLibJars(bootClassPathLibs);
	if (bootClassPath.size() > 0)
	{
		vmOptionLines.push_back(std::string("-Xbootclasspath/a:") + bootClassPath);
	}

	return true;
}

void AddPredefinedVMOptions(std::vector<std::string>& vmOptionLines)
{
	std::string vmOptions = LoadStdString(IDS_VM_OPTIONS);
	while(vmOptions.size() > 0)
	{
		int pos = vmOptions.find(' ');
		if (pos == std::string::npos) pos = vmOptions.size();
		vmOptionLines.push_back(vmOptions.substr(0, pos));
		while(pos < vmOptions.size() && vmOptions[pos] == ' ') pos++;
		vmOptions = vmOptions.substr(pos);
	}

	char ideaProperties[_MAX_PATH];
	if (GetEnvironmentVariableA("IDEA_PROPERTIES", ideaProperties, _MAX_PATH-1))
	{
		vmOptionLines.push_back(std::string("-Didea.properties.file=") + ideaProperties);
	}
}

bool LoadVMOptions()
{
	TCHAR optionsFileName[_MAX_PATH];
	if (LoadString(hInst, IDS_VM_OPTIONS_PATH, optionsFileName, _MAX_PATH-1))
	{
		TCHAR fullOptionsFileName[_MAX_PATH];
		ExpandEnvironmentStrings(optionsFileName, fullOptionsFileName, _MAX_PATH-1);
		
		if (GetFileAttributes(fullOptionsFileName) == INVALID_FILE_ATTRIBUTES)
		{
			GetModuleFileName(NULL, fullOptionsFileName, _MAX_PATH-1);
			_tcscat_s(fullOptionsFileName, _T(".vmoptions"));
		}
		
		std::vector<std::string> vmOptionLines;
		if (LoadVMOptionsFile(fullOptionsFileName, vmOptionLines))
		{
			if (!AddClassPathOptions(vmOptionLines)) return false;
			AddPredefinedVMOptions(vmOptionLines);

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
	std::string binDir = dllName + "\\bin";
	std::string serverDllName = binDir + "\\server\\jvm.dll";
	std::string clientDllName = binDir + "\\client\\jvm.dll";
	if ((bServerJVM && FileExists(serverDllName)) || !FileExists(clientDllName))
	{
		dllName = serverDllName;
	}
	else
	{
		dllName = clientDllName;
	}

	SetCurrentDirectoryA(binDir.c_str());   // ensure that we can find msvcr100.dll which is located in jre/bin directory
	hJVM = LoadLibraryA(dllName.c_str());
	if (hJVM)
	{
		pCreateJavaVM = (JNI_createJavaVM) GetProcAddress(hJVM, "JNI_CreateJavaVM");
	}
	if (!pCreateJavaVM)
	{
		char buf[_MAX_PATH];
		sprintf(buf, "Failed to load JVM DLL %s", dllName.c_str());
		MessageBoxA(NULL, buf, "Error Launching IntelliJ Platform", MB_OK);
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
		TCHAR buf[_MAX_PATH];
		_stprintf_s(buf, _T("Failed to create JVM: error code %d"), result);
		MessageBox(NULL, buf, _T("Error launching IntelliJ Platform"), MB_OK);
	}

	return result == JNI_OK;
}

jobjectArray PrepareCommandLine()
{
	int numArgs;
	LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &numArgs);
	jclass stringClass = env->FindClass("java/lang/String");
	jobjectArray args = env->NewObjectArray(numArgs-1, stringClass, NULL); 
	for(int i=1; i<numArgs; i++)
	{
		const wchar_t* arg = argv[i];
		env->SetObjectArrayElement(args, i-1, env->NewString((const jchar *) arg, wcslen(argv[i])));
	}
	return args;
}

bool RunMainClass()
{
	std::string mainClassName = LoadStdString(IDS_MAIN_CLASS);
	jclass mainClass = env->FindClass(mainClassName.c_str());
	if (!mainClass)
	{
		char buf[_MAX_PATH];
		sprintf_s(buf, "Could not find main class %s", mainClassName.c_str());
		MessageBoxA(NULL, buf, "Error Launching IntelliJ Platform", MB_OK);
		return false;
	}

	jmethodID mainMethod = env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
	if (!mainMethod)
	{
		MessageBoxA(NULL, "Could not find main method", "Error Launching IntelliJ Platform", MB_OK);
		return false;
	}

	jobjectArray args = PrepareCommandLine();
	env->CallStaticVoidMethod(mainClass, mainMethod, args);
	jthrowable exc = env->ExceptionOccurred();
	if (exc)
	{
		MessageBox(NULL, _T("Error invoking main method"), _T("Error launching IntelliJ Platform"), MB_OK);
	}

	return true;
}

void CallCommandLineProcessor(const std::wstring& curDir, const std::wstring& args)
{
	JNIEnv *env;
	JavaVMAttachArgs attachArgs;
	attachArgs.version = JNI_VERSION_1_2;
	attachArgs.name = "WinLauncher external command processing thread";
	attachArgs.group = NULL;
	jvm->AttachCurrentThread((void**) &env, &attachArgs);

	std::string processorClassName = LoadStdString(IDS_COMMAND_LINE_PROCESSOR_CLASS);
	jclass processorClass = env->FindClass(processorClassName.c_str());
	if (processorClass)
	{
		jmethodID processMethodID = env->GetStaticMethodID(processorClass, "processWindowsLauncherCommandLine", "(Ljava/lang/String;Ljava/lang/String;)V");
		if (processMethodID)
		{
			jstring jCurDir = env->NewString((const jchar *) curDir.c_str(), curDir.size());
			jstring jArgs = env->NewString((const jchar *) args.c_str(), args.size());
			env->CallStaticVoidMethod(processorClass, processMethodID, jCurDir, jArgs);
			jthrowable exc = env->ExceptionOccurred();
			if (exc)
			{
				MessageBox(NULL, _T("Error sending command line to existing instance"), _T("Error"), MB_OK);
			}
		}
	}

	jvm->DetachCurrentThread();
}

DWORD WINAPI SingleInstanceThread(LPVOID args)
{
	while(true)
	{
		WaitForSingleObject(hEvent, INFINITE);
		if (terminating) break;

		wchar_t *view = static_cast<wchar_t *>(MapViewOfFile(hFileMapping, FILE_MAP_ALL_ACCESS, 0, 0, 0));
		if (!view) continue;
		std::wstring command(view);
		int pos = command.find('\n');
		if (pos >= 0)
		{
			std::wstring curDir = command.substr(0, pos);
			std::wstring args = command.substr(pos+1);

			CallCommandLineProcessor(curDir, args);
		}

		UnmapViewOfFile(view);
	}
	return 0;
}

void SendCommandLineToFirstInstance()
{
	wchar_t curDir[_MAX_PATH];
	GetCurrentDirectoryW(_MAX_PATH-1, curDir);
	std::wstring command(curDir);
	command += _T("\n");
	command += GetCommandLineW();

	void *view = MapViewOfFile(hFileMapping, FILE_MAP_ALL_ACCESS, 0, 0, 0);
	if (view)
	{
		memcpy(view, command.c_str(), (command.size()+1) * sizeof(wchar_t));
		UnmapViewOfFile(view);
	}
	SetEvent(hEvent);	
}

bool CheckSingleInstance()
{
	char moduleFileName[_MAX_PATH];
	GetModuleFileNameA(NULL, moduleFileName, _MAX_PATH-1);
	for(char *p = moduleFileName; *p; p++)
	{
		if (*p == ':' || *p == '\\') *p = '_';
	}
	std::string mappingName = std::string("IntelliJLauncherMapping.") + moduleFileName;
	std::string eventName = std::string("IntelliJLauncherEvent.") + moduleFileName;

	hEvent = CreateEventA(NULL, FALSE, FALSE, eventName.c_str());

	hFileMapping = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, mappingName.c_str());
	if (!hFileMapping)
	{
		hFileMapping = CreateFileMappingA(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, FILE_MAPPING_SIZE, 
			mappingName.c_str());
		return true;
	}
	else
	{
		SendCommandLineToFirstInstance();
		CloseHandle(hFileMapping);
		CloseHandle(hEvent);
		return false;
	}
}

void DrawSplashImage(HWND hWnd)
{
	HBITMAP hSplashBitmap = (HBITMAP) GetWindowLongPtr(hWnd, GWLP_USERDATA);
	PAINTSTRUCT ps;
	HDC hDC = BeginPaint(hWnd, &ps);
	HDC hMemDC = CreateCompatibleDC(hDC);
	HBITMAP hOldBmp = (HBITMAP) SelectObject(hMemDC, hSplashBitmap);   
	BITMAP splashBitmap;
	GetObject(hSplashBitmap, sizeof(splashBitmap), &splashBitmap);
	BitBlt(hDC, 0, 0, splashBitmap.bmWidth, splashBitmap.bmHeight, hMemDC, 0, 0, SRCCOPY);
	SelectObject(hMemDC, hOldBmp);
	DeleteDC(hMemDC);
	EndPaint(hWnd, &ps);
}

LRESULT CALLBACK SplashScreenWndProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	switch(uMsg)
	{
	case WM_PAINT:
		DrawSplashImage(hWnd);
		break;
		}
	return DefWindowProc(hWnd, uMsg, wParam, lParam);
}

const TCHAR splashClassName[] = _T("IntelliJLauncherSplash");

void RegisterSplashScreenWndClass()
{
	WNDCLASSEX wcx;
	wcx.cbSize = sizeof(wcx);
	wcx.style = 0;
	wcx.lpfnWndProc = SplashScreenWndProc;
	wcx.cbClsExtra = 0;
	wcx.cbWndExtra = 0;
	wcx.hInstance = hInst;
	wcx.hIcon = 0;
	wcx.hCursor = LoadCursor(NULL, IDC_WAIT);
	wcx.hbrBackground = (HBRUSH) GetStockObject(LTGRAY_BRUSH);
	wcx.lpszMenuName = 0;
	wcx.lpszClassName = splashClassName;
	wcx.hIconSm = 0;

	RegisterClassEx(&wcx);
}

HWND ShowSplashScreenWindow(HBITMAP hSplashBitmap)
{
	RECT workArea;
	SystemParametersInfo(SPI_GETWORKAREA, 0, &workArea, 0);
	BITMAP splashBitmap;
	GetObject(hSplashBitmap, sizeof(splashBitmap), &splashBitmap);
	int x = workArea.left + ((workArea.right - workArea.left) - splashBitmap.bmWidth) / 2;
	int y = workArea.top + ((workArea.bottom - workArea.top) - splashBitmap.bmHeight) / 2;
	
	HWND splashWindow = CreateWindowEx(WS_EX_TOOLWINDOW, splashClassName, splashClassName, WS_POPUP,
		x, y, splashBitmap.bmWidth, splashBitmap.bmHeight, NULL, NULL, NULL, NULL);
	SetWindowLongPtr(splashWindow, GWLP_USERDATA, (LONG_PTR) hSplashBitmap);
	ShowWindow(splashWindow, SW_SHOW);
	UpdateWindow(splashWindow);
	return splashWindow;
}

BOOL CALLBACK EnumWindowsProc(HWND hWnd, LPARAM lParam)
{
	static DWORD currentProcId = GetCurrentProcessId();
	DWORD procId = 0;
	GetWindowThreadProcessId(hWnd, &procId);
	if(currentProcId == procId) 
	{
		TCHAR className[_MAX_PATH];
		GetClassName(hWnd, className, _MAX_PATH-1);
		if (_tcscmp(className, splashClassName) != 0)
		{
			WINDOWINFO wi;
			wi.cbSize = sizeof(WINDOWINFO);
			GetWindowInfo(hWnd, &wi);
			if((wi.dwStyle & WS_VISIBLE) != 0) 
			{
				HWND *phNewWindow = (HWND *) lParam;
				*phNewWindow = hWnd;
				return FALSE;
			}
		}
	}
	return TRUE;
}


DWORD WINAPI SplashScreenThread(LPVOID args)
{
	HBITMAP hSplashBitmap = static_cast<HBITMAP>(args);
	RegisterSplashScreenWndClass();
	HWND splashWindow = ShowSplashScreenWindow(hSplashBitmap);

	MSG msg;
	while(true)
	{
		while (PeekMessage(&msg, splashWindow, 0, 0, PM_REMOVE)) 
		{
			TranslateMessage(&msg);
			DispatchMessage(&msg);
		}
		Sleep(50);
		HWND hNewWindow = NULL;
		EnumWindows(EnumWindowsProc, (LPARAM) &hNewWindow);
		if (hNewWindow)
		{
			BringWindowToTop(hNewWindow);
			DeleteObject(hSplashBitmap);
			DestroyWindow(splashWindow);
		}
	}
	return 0;
}

int APIENTRY _tWinMain(HINSTANCE hInstance,
                     HINSTANCE hPrevInstance,
                     LPTSTR    lpCmdLine,
                     int       nCmdShow)
{
	UNREFERENCED_PARAMETER(hPrevInstance);
	UNREFERENCED_PARAMETER(lpCmdLine);

	hInst = hInstance;

	if (!CheckSingleInstance()) return 1;
	
	HANDLE hSplashBitmap = static_cast<HBITMAP>(LoadImage(hInst, MAKEINTRESOURCE(IDB_SPLASH), IMAGE_BITMAP, 0, 0, 0));
	if (hSplashBitmap)
	{
		CreateThread(NULL, 0, SplashScreenThread, hSplashBitmap, 0, NULL);
	}

	if (!LocateJVM()) return 1;
	if (!LoadVMOptions()) return 1;
	if (!LoadJVMLibrary()) return 1;
	if (!CreateJVM()) return 1;

	hSingleInstanceWatcherThread = CreateThread(NULL, 0, SingleInstanceThread, NULL, 0, NULL);

	if (!RunMainClass()) return 1;

	jvm->DestroyJavaVM();

	terminating = true;
	SetEvent(hEvent);
	WaitForSingleObject(hSingleInstanceWatcherThread, INFINITE);
	CloseHandle(hEvent);
	CloseHandle(hFileMapping);

	return 0;
}
 