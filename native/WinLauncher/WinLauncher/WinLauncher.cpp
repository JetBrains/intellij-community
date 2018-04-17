/*
* Copyright 2000-2018 JetBrains s.r.o.
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

typedef JNIIMPORT jint(JNICALL *JNI_createJavaVM)(JavaVM **pvm, JNIEnv **env, void *args);

HINSTANCE hInst; // Current instance.
char jvmPath[_MAX_PATH] = "";
JavaVMOption* vmOptions = NULL;
int vmOptionCount = 0;
bool bServerJVM = false;
HMODULE hJVM = NULL;
JNI_createJavaVM pCreateJavaVM = NULL;
JavaVM* jvm = NULL;
JNIEnv* env = NULL;
volatile bool terminating = false;

//tools.jar doesn't exist in jdk 9 and later. So check it for jdk 1.8 only.
bool toolsArchiveExists = true;

HANDLE hFileMapping;
HANDLE hEvent;
HANDLE hSingleInstanceWatcherThread;
const int FILE_MAPPING_SIZE = 16000;

#ifdef _M_X64
bool need64BitJRE = true;
#define BITS_STR "64-bit"
#else
bool need64BitJRE = false;
#define BITS_STR "32-bit"
#endif

void TrimLine(char* line);

std::string EncodeWideACP(const std::wstring &str)
{
  int cbANSI = WideCharToMultiByte(CP_ACP, 0, str.c_str(), str.size(), NULL, 0, NULL, NULL);
  char* ansiBuf = new char[cbANSI];
  WideCharToMultiByte(CP_ACP, 0, str.c_str(), str.size(), ansiBuf, cbANSI, NULL, NULL);
  std::string result(ansiBuf, cbANSI);
  delete[] ansiBuf;
  return result;
}

std::string LoadStdString(int id)
{
  wchar_t *buf = NULL;
  int len = LoadStringW(hInst, id, reinterpret_cast<LPWSTR>(&buf), 0);
  return len ? EncodeWideACP(std::wstring(buf, len)) : "";
}

bool FileExists(const std::string& path)
{
  return GetFileAttributesA(path.c_str()) != INVALID_FILE_ATTRIBUTES;
}

bool IsValidJRE(const char* path)
{
  std::string dllPath(path);
  if (dllPath[dllPath.size() - 1] != '\\')
  {
    dllPath += "\\";
  }
  return FileExists(dllPath + "bin\\server\\jvm.dll") || FileExists(dllPath + "bin\\client\\jvm.dll");
}

bool Is64BitJRE(const char* path)
{
  std::string cfgPath(path);
  std::string cfgJava9Path(path);
  cfgPath += "\\lib\\amd64\\jvm.cfg";
  cfgJava9Path += "\\lib\\jvm.cfg";
  return FileExists(cfgPath) || FileExists(cfgJava9Path);
}

bool FindValidJVM(const char* path)
{
  if (IsValidJRE(path))
  {
    strcpy_s(jvmPath, _MAX_PATH - 1, path);
    return true;
  }
  char jrePath[_MAX_PATH];
  strcpy_s(jrePath, path);
  if (jrePath[strlen(jrePath) - 1] != '\\')
  {
    strcat_s(jrePath, "\\");
  }
  strcat_s(jrePath, _MAX_PATH - 1, "jre");
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
  GetModuleFileNameA(NULL, libDir, _MAX_PATH - 1);
  char* lastSlash = strrchr(libDir, '\\');
  if (!lastSlash) return "";
  *lastSlash = '\0';
  lastSlash = strrchr(libDir, '\\');
  if (!lastSlash) return "";
  strcpy(lastSlash + 1, suffix);
  strcat_s(libDir, "\\");
  return std::string(libDir);
}

bool FindJVMInEnvVar(const char* envVarName, bool& result)
{
  char envVarValue[_MAX_PATH];
  if (GetEnvironmentVariableA(envVarName, envVarValue, _MAX_PATH - 1))
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
      std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
      MessageBoxA(NULL, buf, error.c_str(), MB_OK);
      result = false;
    }
    return true;
  }
  return false;
}

bool FindJVMInSettings() {
  TCHAR buffer[_MAX_PATH];
  TCHAR copy[_MAX_PATH];

  GetModuleFileName(NULL, buffer, _MAX_PATH);
  std::wstring module(buffer);

  if (LoadString(hInst, IDS_VM_OPTIONS_PATH, buffer, _MAX_PATH)) {
    ExpandEnvironmentStrings(buffer, copy, _MAX_PATH - 1);
    std::wstring path(copy);
    path += module.substr(module.find_last_of('\\')) + L".jdk";
    FILE *f = _tfopen(path.c_str(), _T("rt"));
    if (!f) return false;

    char line[_MAX_PATH];
    if (!fgets(line, _MAX_PATH, f)) {
      fclose(f);
      return false;
    }

    TrimLine(line);
    fclose(f);

    return FindValidJVM(line);
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
  DWORD javaHomeSize = _MAX_PATH - 1;
  bool success = false;
  if (RegQueryValueExA(hKey, "JavaHome", NULL, NULL, (LPBYTE)javaHome, &javaHomeSize) == ERROR_SUCCESS)
  {
    success = FindValidJVM(javaHome);
  }
  RegCloseKey(hKey);
  return success;
}

bool FindJVMInRegistryWithVersion(const char* version, bool wow64_32)
{
  char* keyName = "Java Runtime Environment";
  // starting from java 9 key name has been changed
  char* jreKeyName = "JRE";
  char* jdkKeyName = "JDK";

  bool foundJava = false;
  char buf[_MAX_PATH];
  //search jre in registry if the product doesn't require tools.jar
  if (LoadStdString(IDS_JDK_ONLY) != std::string("true")) {
    sprintf_s(buf, "Software\\JavaSoft\\%s\\%s", keyName, version);
    foundJava = FindJVMInRegistryKey(buf, wow64_32);
    if (!foundJava) {
      sprintf_s(buf, "Software\\JavaSoft\\%s\\%s", jreKeyName, version);
      foundJava = FindJVMInRegistryKey(buf, wow64_32);
    }
  }

  //search jdk in registry if the product requires tools.jar or jre isn't installed.
  if (!foundJava) {
    keyName = "Java Development Kit";
    sprintf_s(buf, "Software\\JavaSoft\\%s\\%s", keyName, version);
    foundJava = FindJVMInRegistryKey(buf, wow64_32);
    if (!foundJava) {
      sprintf_s(buf, "Software\\JavaSoft\\%s\\%s", jdkKeyName, version);
      foundJava = FindJVMInRegistryKey(buf, wow64_32);
    }
  }
  return foundJava;
}

bool FindJVMInRegistry()
{
#ifndef _M_X64
  if (FindJVMInRegistryWithVersion("1.8", true))
    return true;
  if (FindJVMInRegistryWithVersion("9", true))
    toolsArchiveExists = false;
    return true;
  if (FindJVMInRegistryWithVersion("10", true))
    toolsArchiveExists = false;
    return true;

  //obsolete java versions
  if (FindJVMInRegistryWithVersion("1.7", true))
    return true;
  if (FindJVMInRegistryWithVersion("1.6", true))
    return true;
#endif

  if (FindJVMInRegistryWithVersion("1.8", false))
    return true;
  if (FindJVMInRegistryWithVersion("9", false))
    toolsArchiveExists = false;
    return true;
  if (FindJVMInRegistryWithVersion("10", false))
    toolsArchiveExists = false;
    return true;

  //obsolete java versions
  if (FindJVMInRegistryWithVersion("1.7", false))
    return true;
  if (FindJVMInRegistryWithVersion("1.6", false))
    return true;
  return false;
}

// The following code is taken from http://msdn.microsoft.com/en-us/library/ms684139(v=vs.85).aspx
// and provides a backwards compatible way to check if this application is a 32-bit process running
// on a 64-bit OS
typedef BOOL (WINAPI *LPFN_ISWOW64PROCESS) (HANDLE, PBOOL);

LPFN_ISWOW64PROCESS fnIsWow64Process;

BOOL IsWow64()
{
  BOOL bIsWow64 = FALSE;

  //IsWow64Process is not available on all supported versions of Windows.
  //Use GetModuleHandle to get a handle to the DLL that contains the function
  //and GetProcAddress to get a pointer to the function if available.

  fnIsWow64Process = (LPFN_ISWOW64PROCESS) GetProcAddress(
      GetModuleHandle(TEXT("kernel32")), "IsWow64Process");

  if (NULL != fnIsWow64Process)
  {
    fnIsWow64Process(GetCurrentProcess(), &bIsWow64);
  }
  return bIsWow64;
}

bool LocateJVM()
{
  bool result;
  if (FindJVMInEnvVar(LoadStdString(IDS_JDK_ENV_VAR).c_str(), result))
  {
    return result;
  }

  if (FindJVMInSettings()) return true;

  std::vector<std::string> jrePaths;
  if(need64BitJRE) jrePaths.push_back(GetAdjacentDir("jre64"));
  jrePaths.push_back(GetAdjacentDir("jre32"));
  jrePaths.push_back(GetAdjacentDir("jre"));
  for(std::vector<std::string>::iterator it = jrePaths.begin(); it != jrePaths.end(); ++it) {
    if (FindValidJVM((*it).c_str()) && Is64BitJRE(jvmPath) == need64BitJRE)
    {
      return true;
    }
  }

  if (FindJVMInEnvVar("JAVA_HOME", result))
  {
    return result;
  }

  if (FindJVMInRegistry())
  {
    return true;
  }

  std::string jvmError;
  jvmError = "No JVM installation found. Please install a " BITS_STR " JDK.\n"
    "If you already have a JDK installed, define a JAVA_HOME variable in\n"
    "Computer > System Properties > System Settings > Environment Variables.";

  if (IsWow64())
  {
    // If WoW64, this means we are running a 32-bit program on 64-bit Windows. This may explain
    // why we couldn't locate the JVM.
    jvmError += "\n\nNOTE: We have detected that you are running a 64-bit version of the "
        "Windows operating system but are running the 32-bit executable. This "
        "can prevent you from finding a 64-bit installation of Java. Consider running "
        "the 64-bit version instead, if this is the problem you're encountering.";
  }

  std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
  MessageBoxA(NULL, jvmError.c_str(),  error.c_str(), MB_OK);
  return false;
}

void TrimLine(char* line)
{
  char *p = line + strlen(line) - 1;
  if (p >= line && *p == '\n')
  {
    *p-- = '\0';
  }
  while (p >= line && (*p == ' ' || *p == '\t'))
  {
    *p-- = '\0';
  }
}

bool LoadVMOptionsFile(const TCHAR* path, std::vector<std::string>& vmOptionLines)
{
  FILE *f = _tfopen(path, _T("rt"));
  if (!f) return false;

  char line[_MAX_PATH];
  while (fgets(line, _MAX_PATH, f))
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
  std::string baseToolsJarPath = jvmPath;
  // remove trailing slash if any
  size_t lastSlash = baseToolsJarPath.rfind('\\');
  if (lastSlash == baseToolsJarPath.length() - 1)
  {
      baseToolsJarPath = baseToolsJarPath.substr(0, lastSlash);
  }
  // 1) look in the base dir
  std::string toolsJarPath = baseToolsJarPath + "\\lib\\tools.jar";
  if (FileExists(toolsJarPath))
  {
    return toolsJarPath;
  }
  // 2) look in the up dir
  lastSlash = baseToolsJarPath.rfind('\\');
  if (lastSlash != std::string::npos)
  {
    toolsJarPath = baseToolsJarPath.substr(0, lastSlash + 1) + "lib\\tools.jar";
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
  while (pos < jarList.size())
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
    result += jarList.substr(pos, delimiterPos - pos);
    pos = delimiterPos + 1;
  }
  return result;
}

std::string BuildClassPath()
{
  std::string classpathLibs = LoadStdString(IDS_CLASSPATH_LIBS);
  std::string result = CollectLibJars(classpathLibs);

  if (toolsArchiveExists)
  {
    std::string toolsJar = FindToolsJar();
    if (toolsJar.size() > 0)
    {
      result += ";";
      result += toolsJar;
    }
  }

  return result;
}

bool AddClassPathOptions(std::vector<std::string>& vmOptionLines)
{
  std::string classPath = BuildClassPath();
  if (classPath.size() == 0) return false;
  vmOptionLines.push_back(std::string("-Djava.class.path=") + classPath);

  return true;
}

//return VMOptions from one of the files in the following order:
//$<IDE-NAME>_VM_OPTIONS
//$CONFIG_DIRECTORY/<ide-name>[64][.exe].vmoptions
//bin/<ide-name>[64][.exe].vmoptions
bool FindValidVMOptions(std::vector<std::wstring> files, std::wstring& used, std::vector<std::string>& vmOptionLines)
{
  if (files.size() != 0)
  {
    for (int i = 0; i < files.size(); i++)
    {
      if (GetFileAttributes(files[i].c_str()) != INVALID_FILE_ATTRIBUTES)
      {
        if (LoadVMOptionsFile(files[i].c_str(), vmOptionLines))
        {
          used += (used.size() ? L"," : L"") + files[i];
          return true;
        }
      }
    }
  }
  return false;
}

std::string getVMOption(int resource){
  TCHAR buffer[_MAX_PATH];
  TCHAR copy[_MAX_PATH];
  std::string vmOption = "";
  if (LoadString(hInst, resource, buffer, _MAX_PATH))
  {
    ExpandEnvironmentStrings(buffer, copy, _MAX_PATH);
    std::wstring module(copy);
    vmOption = std::string(module.begin(), module.end());
  }
  return vmOption;
}

void AddPredefinedVMOptions(std::vector<std::string>& vmOptionLines)
{
  std::string vmOptions = LoadStdString(IDS_VM_OPTIONS);
  while (vmOptions.size() > 0)
  {
    int pos = vmOptions.find(' ');
    if (pos == std::string::npos) pos = vmOptions.size();
    vmOptionLines.push_back(vmOptions.substr(0, pos));
    while (pos < vmOptions.size() && vmOptions[pos] == ' ') pos++;
    vmOptions = vmOptions.substr(pos);
  }

  std::string errorFile = getVMOption(IDS_VM_OPTION_ERRORFILE);
  std::string heapDumpPath = getVMOption(IDS_VM_OPTION_HEAPDUMPPATH);
  if (errorFile != "") vmOptionLines.push_back(errorFile);
  if (heapDumpPath != "") vmOptionLines.push_back(heapDumpPath);

  char propertiesFile[_MAX_PATH];
  if (GetEnvironmentVariableA(LoadStdString(IDS_PROPS_ENV_VAR).c_str(), propertiesFile, _MAX_PATH))
  {
    vmOptionLines.push_back(std::string("-Didea.properties.file=") + propertiesFile);
  }
}

bool LoadVMOptions()
{
  TCHAR buffer[_MAX_PATH];
  TCHAR copy[_MAX_PATH];

  std::vector<std::wstring> files;

  GetModuleFileName(NULL, buffer, _MAX_PATH);
  std::wstring module(buffer);

  if (LoadString(hInst, IDS_VM_OPTIONS_ENV_VAR, buffer, _MAX_PATH))
  {
    if (GetEnvironmentVariableW(buffer, copy, _MAX_PATH)) {
      ExpandEnvironmentStrings(copy, buffer, _MAX_PATH);
      files.push_back(std::wstring(buffer));
    }
  }

  std::wstring::size_type pos = module.find_last_of(L"\\bin\\", -1);
  if (pos > 0)
  {
      files.push_back(module.substr(0, pos - 5) + L".vmoptions");
  }

  if (LoadString(hInst, IDS_VM_OPTIONS_PATH, buffer, _MAX_PATH))
  {
    ExpandEnvironmentStrings(buffer, copy, _MAX_PATH - 1);
    std::wstring selector(copy);
    files.push_back(selector + module.substr(module.find_last_of('\\')) + L".vmoptions");
  }

  files.push_back(module + L".vmoptions");

  std::wstring used;
  std::vector<std::string> vmOptionLines;

  if (!FindValidVMOptions(files, used, vmOptionLines))
  {
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, "Cannot find VM options file", error.c_str(), MB_OK);
    return false;
  }

  vmOptionLines.push_back(std::string("-Djb.vmOptionsFile=") + EncodeWideACP(used));

  if (!AddClassPathOptions(vmOptionLines)) return false;
  AddPredefinedVMOptions(vmOptionLines);

  vmOptionCount = vmOptionLines.size();
  vmOptions = (JavaVMOption*)malloc(vmOptionCount * sizeof(JavaVMOption));
  for (int i = 0; i < vmOptionLines.size(); i++)
  {
    vmOptions[i].optionString = _strdup(vmOptionLines[i].c_str());
    vmOptions[i].extraInfo = 0;
  }

  return true;
}

bool LoadJVMLibrary()
{
  std::string dllName(jvmPath);
  std::string binDir = dllName + "\\bin";
  TCHAR currentDir[MAX_PATH];
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

  // ensure we can find msvcr100.dll which is located in jre/bin directory; jvm.dll depends on it.
  GetCurrentDirectory(sizeof(currentDir),currentDir);
  SetCurrentDirectoryA(binDir.c_str());
  hJVM = LoadLibraryA(dllName.c_str());
  if (hJVM)
  {
    pCreateJavaVM = (JNI_createJavaVM) GetProcAddress(hJVM, "JNI_CreateJavaVM");
  }
  SetCurrentDirectory(currentDir);
  if (!pCreateJavaVM)
  {
    std::string jvmError = "Failed to load JVM DLL ";
    jvmError += dllName.c_str();
    jvmError += "\n"
        "If you already have a " BITS_STR " JDK installed, define a JAVA_HOME variable in "
        "Computer > System Properties > System Settings > Environment Variables.";
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, jvmError.c_str(), error.c_str(), MB_OK);
    return false;
  }
  return true;
}

bool IsJBRE()
{
  if (!env) return false;

  jclass cls = env->FindClass("java/lang/System");
  if (!cls) return false;

  jmethodID method = env->GetStaticMethodID(cls, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
  if (!method) return false;

  jstring jvendor = (jstring)env->CallStaticObjectMethod(cls, method, env->NewStringUTF("java.vendor"));
  if (!jvendor) return false;

  const char *cvendor = env->GetStringUTFChars(jvendor, NULL);
  bool isJB = strstr(cvendor, "JetBrains") != NULL;
  env->ReleaseStringUTFChars(jvendor, cvendor);

  return isJB;
}

void SetProcessDPIAwareProperty()
{
    typedef BOOL (WINAPI SetProcessDPIAwareFunc)(void);
    HMODULE hLibUser32Dll = ::LoadLibraryA("user32.dll");

    if (hLibUser32Dll != NULL) {
        SetProcessDPIAwareFunc *lpSetProcessDPIAware =
            (SetProcessDPIAwareFunc*)::GetProcAddress(hLibUser32Dll, "SetProcessDPIAware");
        if (lpSetProcessDPIAware != NULL) {
            lpSetProcessDPIAware();
        }
        ::FreeLibrary(hLibUser32Dll);
    }
}

std::string getErrorMessage(int errorCode)
{
  std::string errorMessage = "";
  if (errorCode == -6)
  {
      errorMessage = "MaxJavaStackTraceDepth=-1 is outside the allowed range [ 0 ... 1073741823 ].\nImproperly specified VM option 'MaxJavaStackTraceDepth=-1'\n";
  }
  return errorMessage;
}

bool CreateJVM()
{
  JavaVMInitArgs initArgs;
  initArgs.version = JNI_VERSION_1_2;
  initArgs.options = vmOptions;
  initArgs.nOptions = vmOptionCount;
  initArgs.ignoreUnrecognized = JNI_FALSE;

  int result = pCreateJavaVM(&jvm, &env, &initArgs);

  for (int i = 0; i < vmOptionCount; i++)
  {
    free(vmOptions[i].optionString);
  }
  free(vmOptions);
  vmOptions = NULL;

  if (result != JNI_OK)
  {
    std::stringstream buf;
    std::string jvmError = getErrorMessage(result);
    if (jvmError == "") {
        jvmError = "If you already have a " BITS_STR " JDK installed, define a JAVA_HOME variable in \n";
        jvmError += "Computer > System Properties > System Settings > Environment Variables.\n";
    }

    buf << jvmError;
    buf << "\nFailed to create JVM. ";
    buf << "JVM Path: " << jvmPath;
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, buf.str().c_str(), error.c_str(), MB_OK);
  }

  // Set DPI-awareness here or let JBRE do that.
  if (!IsJBRE()) SetProcessDPIAwareProperty();

  return result == JNI_OK;
}

jobjectArray ArgsToJavaArray(std::vector<LPWSTR> args)
{
  jclass stringClass = env->FindClass("java/lang/String");
  jobjectArray result = env->NewObjectArray(args.size(), stringClass, NULL);
  for (int i = 0; i < args.size(); i++)
  {
     env->SetObjectArrayElement(result, i, env->NewString((const jchar *)args[i], wcslen(args[i])));
  }
  return result;
}

std::vector<LPWSTR> ParseCommandLine(LPCWSTR commandLine)
{
  int numArgs;
  LPWSTR* argv = CommandLineToArgvW(commandLine, &numArgs);

  // skip process name
  std::vector<LPWSTR> result;
  for (int i = 1; i < numArgs; i++)
  {
    result.push_back(argv[i]);
  }
  return result;
}

std::vector<LPWSTR> RemovePredefinedArgs(std::vector<LPWSTR> args)
{
  std::vector<LPWSTR> result;
  for (int i = 0; i < args.size(); i++)
  {
    if (_wcsicmp(args[i], _T("/nativesplash")) == 0) continue;
    result.push_back(args[i]);
  }
  return result;
}

bool RunMainClass(std::vector<LPWSTR> args)
{
  std::string mainClassName = LoadStdString(IDS_MAIN_CLASS);
  jclass mainClass = env->FindClass(mainClassName.c_str());
  if (!mainClass)
  {
    char buf[_MAX_PATH + 256];
    sprintf_s(buf, "Could not find main class %s", mainClassName.c_str());
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, buf, error.c_str(), MB_OK);
    return false;
  }

  jmethodID mainMethod = env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
  if (!mainMethod)
  {
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, "Could not find main method", error.c_str(), MB_OK);
    return false;
  }

  env->CallStaticVoidMethod(mainClass, mainMethod, ArgsToJavaArray(args));
  jthrowable exc = env->ExceptionOccurred();
  if (exc)
  {
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, "Error invoking main method", error.c_str(), MB_OK);
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
  jvm->AttachCurrentThread((void**)&env, &attachArgs);

  std::string processorClassName = LoadStdString(IDS_COMMAND_LINE_PROCESSOR_CLASS);
  jclass processorClass = env->FindClass(processorClassName.c_str());
  if (processorClass)
  {
    jmethodID processMethodID = env->GetStaticMethodID(processorClass, "processWindowsLauncherCommandLine", "(Ljava/lang/String;[Ljava/lang/String;)V");
    if (processMethodID)
    {
      jstring jCurDir = env->NewString((const jchar *)curDir.c_str(), curDir.size());
      jobjectArray jArgs = ArgsToJavaArray(RemovePredefinedArgs(ParseCommandLine(args.c_str())));
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
  while (true)
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
      std::wstring args = command.substr(pos + 1);

      CallCommandLineProcessor(curDir, args);
    }

    UnmapViewOfFile(view);
  }
  return 0;
}

void SendCommandLineToFirstInstance()
{
  wchar_t curDir[_MAX_PATH];
  GetCurrentDirectoryW(_MAX_PATH - 1, curDir);
  std::wstring command(curDir);
  command += _T("\n");
  command += GetCommandLineW();

  void *view = MapViewOfFile(hFileMapping, FILE_MAP_ALL_ACCESS, 0, 0, 0);
  if (view)
  {
    memcpy(view, command.c_str(), (command.size() + 1) * sizeof(wchar_t));
    UnmapViewOfFile(view);
  }
  SetEvent(hEvent);
}

bool CheckSingleInstance()
{
  char moduleFileName[_MAX_PATH];
  GetModuleFileNameA(NULL, moduleFileName, _MAX_PATH - 1);
  for (char *p = moduleFileName; *p; p++)
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
  HBITMAP hSplashBitmap = (HBITMAP)GetWindowLongPtr(hWnd, GWLP_USERDATA);
  PAINTSTRUCT ps;
  HDC hDC = BeginPaint(hWnd, &ps);
  HDC hMemDC = CreateCompatibleDC(hDC);
  HBITMAP hOldBmp = (HBITMAP)SelectObject(hMemDC, hSplashBitmap);
  BITMAP splashBitmap;
  GetObject(hSplashBitmap, sizeof(splashBitmap), &splashBitmap);
  BitBlt(hDC, 0, 0, splashBitmap.bmWidth, splashBitmap.bmHeight, hMemDC, 0, 0, SRCCOPY);
  SelectObject(hMemDC, hOldBmp);
  DeleteDC(hMemDC);
  EndPaint(hWnd, &ps);
}

LRESULT CALLBACK SplashScreenWndProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
  switch (uMsg)
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
  wcx.hbrBackground = (HBRUSH)GetStockObject(LTGRAY_BRUSH);
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
  SetWindowLongPtr(splashWindow, GWLP_USERDATA, (LONG_PTR)hSplashBitmap);
  ShowWindow(splashWindow, SW_SHOW);
  UpdateWindow(splashWindow);
  return splashWindow;
}

DWORD parentProcId;
HANDLE parentProcHandle;

BOOL IsParentProcessRunning(HANDLE hProcess)
{
  if (hProcess == NULL) return FALSE;
  DWORD ret = WaitForSingleObject(hProcess, 0);
  return ret == WAIT_TIMEOUT;
}

BOOL CALLBACK EnumWindowsProc(HWND hWnd, LPARAM lParam)
{
  DWORD procId = 0;
  GetWindowThreadProcessId(hWnd, &procId);
  if (parentProcId == procId)
  {
    WINDOWINFO wi;
    wi.cbSize = sizeof(WINDOWINFO);
    GetWindowInfo(hWnd, &wi);
    if ((wi.dwStyle & WS_VISIBLE) != 0)
    {
      HWND *phNewWindow = (HWND *)lParam;
      *phNewWindow = hWnd;
      return FALSE;
    }
  }
  return TRUE;
}

DWORD WINAPI SplashScreen(HBITMAP hSplashBitmap)
{
  RegisterSplashScreenWndClass();
  HWND splashWindow = ShowSplashScreenWindow(hSplashBitmap);
  MSG msg;
  while (true)
  {
    while (PeekMessage(&msg, splashWindow, 0, 0, PM_REMOVE))
    {
      TranslateMessage(&msg);
      DispatchMessage(&msg);
    }
    Sleep(50);
    HWND hNewWindow = NULL;
    EnumWindows(EnumWindowsProc, (LPARAM)&hNewWindow);
    if (hNewWindow)
    {
      BringWindowToTop(hNewWindow);
      Sleep(100);
      DeleteObject(hSplashBitmap);
      DestroyWindow(splashWindow);
      break;
    }
    if (!IsParentProcessRunning(parentProcHandle)) break;
  }
  return 0;
}

void StartSplashProcess()
{
  TCHAR ownPath[_MAX_PATH];
  TCHAR params[_MAX_PATH];

  PROCESS_INFORMATION splashProcessInformation;
  STARTUPINFO startupInfo;
  memset(&splashProcessInformation, 0, sizeof(splashProcessInformation));
  memset(&startupInfo, 0, sizeof(startupInfo));
  startupInfo.cb = sizeof(startupInfo);
  startupInfo.dwFlags = STARTF_USESHOWWINDOW;
  startupInfo.wShowWindow = SW_SHOW;

  GetModuleFileName(NULL, ownPath, (sizeof(ownPath)));
  _snwprintf(params, _MAX_PATH, _T("SPLASH %d"), GetCurrentProcessId());
  if (CreateProcess(ownPath, params, NULL, NULL, FALSE, 0, NULL, NULL, &startupInfo, &splashProcessInformation))
  {
    CloseHandle(splashProcessInformation.hProcess);
    CloseHandle(splashProcessInformation.hThread);
  }
}

int APIENTRY _tWinMain(HINSTANCE hInstance,
                       HINSTANCE hPrevInstance,
                       LPTSTR    lpCmdLine,
                       int       nCmdShow)
{
  UNREFERENCED_PARAMETER(hPrevInstance);

  hInst = hInstance;

  if (__argc == 2 && _wcsicmp(__wargv[0], _T("SPLASH")) == 0)
  {
    HBITMAP hSplashBitmap = static_cast<HBITMAP>(LoadImage(hInst, MAKEINTRESOURCE(IDB_SPLASH), IMAGE_BITMAP, 0, 0, 0));
    if (hSplashBitmap)
    {
      parentProcId = _wtoi(__wargv[1]);
      parentProcHandle = OpenProcess(SYNCHRONIZE, FALSE, parentProcId);
      if (IsParentProcessRunning(parentProcHandle)) SplashScreen(hSplashBitmap);
    }
    CloseHandle(parentProcHandle);
    return 0;
  }

  //it's OK to return 0 here, because the control is transferred to the first instance
  if (!CheckSingleInstance()) return 0;

  std::vector<LPWSTR> args = ParseCommandLine(GetCommandLineW());

  bool nativesplash = false;
  for (int i = 0; i < args.size(); i++)
  {
    if (_wcsicmp(args[i], _T("/nativesplash")) == 0) nativesplash = true;
  }
  args = RemovePredefinedArgs(args);

  if (nativesplash) StartSplashProcess();

  if (!LocateJVM()) return 1;
  if (!LoadVMOptions()) return 1;
  if (!LoadJVMLibrary()) return 1;
  if (!CreateJVM()) return 1;

  hSingleInstanceWatcherThread = CreateThread(NULL, 0, SingleInstanceThread, NULL, 0, NULL);

  if (!RunMainClass(args)) return 1;

  jvm->DestroyJavaVM();

  terminating = true;
  SetEvent(hEvent);
  WaitForSingleObject(hSingleInstanceWatcherThread, INFINITE);
  CloseHandle(hEvent);
  CloseHandle(hFileMapping);

  return 0;
}
