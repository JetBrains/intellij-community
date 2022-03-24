// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <sstream>
#include <vector>
#include <malloc.h>
#include <memory.h>

#include <tchar.h>

#include <Windows.h>
#include <ShellAPI.h>
#include <Shlobj.h>
#include <Knownfolders.h>

#include <jni.h>

#include "resource.h"


typedef JNIIMPORT jint(JNICALL *JNI_createJavaVM)(JavaVM **pvm, JNIEnv **env, void *args);

HINSTANCE hInst; // Current instance.
char jvmPath[_MAX_PATH] = "";
JavaVMOption* vmOptions = NULL;
int vmOptionCount = 0;
HMODULE hJVM = NULL;
JNI_createJavaVM pCreateJavaVM = NULL;
JavaVM* jvm = NULL;
volatile bool terminating = false;
volatile int hookExitCode = 0;

HANDLE hFileMapping;
HANDLE hEvent;
HANDLE hSingleInstanceWatcherThread;
const int FILE_MAPPING_SIZE = 16000;

void TrimLine(char* line);

static std::string EncodeWideACP(const std::wstring &str)
{
  const int cbANSI = WideCharToMultiByte(CP_ACP, 0, str.c_str(), str.size(), NULL, 0, NULL, NULL);
  if (cbANSI <= 0)
    return std::string();

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

static bool IsValidJRE(const std::string& path)
{
  return FileExists(path + "\\bin\\server\\jvm.dll") || FileExists(path + "\\bin\\client\\jvm.dll");
}

bool Is64BitJRE(const char* path)
{
  std::string cfgJava9Path(path);
  std::string accessbridgeVersion(path);
  cfgJava9Path += "\\lib\\jvm.cfg";
  accessbridgeVersion += "\\bin\\windowsaccessbridge-32.dll";
  return FileExists(cfgJava9Path) && !FileExists(accessbridgeVersion);
}

bool FindValidJVM(const char* path)
{
  if (IsValidJRE(path))
  {
    strcpy_s(jvmPath, _MAX_PATH - 1, path);
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
      if (!Is64BitJRE(jvmPath)) return false;
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
    return true;
  if (FindJVMInRegistryWithVersion("10", true))
    return true;
#endif

  if (FindJVMInRegistryWithVersion("1.8", false))
    return true;
  if (FindJVMInRegistryWithVersion("9", false))
    return true;
  if (FindJVMInRegistryWithVersion("10", false))
    return true;
  if (FindJVMInRegistryWithVersion("11", false))
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

  if (FindJVMInSettings()) return true;

  if (FindValidJVM(GetAdjacentDir("jbr").c_str()) && Is64BitJRE(jvmPath))
  {
    return true;
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
  jvmError = "No JVM installation found. Please install a JDK.\n"
    "If you already have a JDK installed, define a JAVA_HOME variable in\n"
    "Computer > System Properties > System Settings > Environment Variables.";

  std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
  MessageBoxA(NULL, jvmError.c_str(), error.c_str(), MB_OK);
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

static bool LoadVMOptionsFile(const char* path, std::vector<std::string>& vmOptionLines) {
  FILE *f = fopen(path, "rt");
  if (!f) return false;

  char line[4096];
  while (fgets(line, sizeof(line), f)) {
    TrimLine(line);
    if (strlen(line) > 0 && line[0] != '#' && strcmp(line, "-server") != 0) {
      vmOptionLines.push_back(line);
    }
  }
  fclose(f);

  return true;
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
  return CollectLibJars(classpathLibs);
}

std::string BuildBootClassPath()
{
  std::string classpathLibs = LoadStdString(IDS_BOOTCLASSPATH_LIBS);
  return CollectLibJars(classpathLibs);
}

bool AddClassPathOptions(std::vector<std::string>& vmOptionLines)
{
  std::string classPath = BuildClassPath();
  if (classPath.size() == 0) return false;
  vmOptionLines.push_back(std::string("-Djava.class.path=") + classPath);

  return true;
}

bool AddBootClassPathOptions(std::vector<std::string>& vmOptionLines)
{
  std::string classPath = BuildBootClassPath();
  if (classPath.size() == 0) return false;
  vmOptionLines.push_back(std::string("-Xbootclasspath/a:") + classPath);

  return true;
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

/*
This hook is passed to JNI in the LoadVMOptions method to catch exit code of java program
*/
void (JNICALL jniExitHook)(jint code) {
  hookExitCode = code;
}

bool LoadVMOptions() {
  char bin_vmoptions[_MAX_PATH], buffer1[_MAX_PATH], buffer2[_MAX_PATH], *vmOptionsFile = NULL;
  std::vector<std::string> lines, user_lines;

  GetModuleFileNameA(NULL, bin_vmoptions, _MAX_PATH);
  strcat_s(bin_vmoptions, ".vmoptions");

  // 1. %<IDE_NAME>_VM_OPTIONS%
  LoadStringA(hInst, IDS_VM_OPTIONS_ENV_VAR, buffer1, _MAX_PATH);
  if (GetEnvironmentVariableA(buffer1, buffer2, _MAX_PATH) != 0 && LoadVMOptionsFile(buffer2, lines)) {
    vmOptionsFile = buffer2;
  }
  else {
    // 2. <IDE_HOME>\bin\<exe_name>.vmoptions ...
    if (LoadVMOptionsFile(bin_vmoptions, lines)) {
      vmOptionsFile = bin_vmoptions;
    }
    // ... [+ <IDE_HOME>.vmoptions (Toolbox) || <config_directory>\<exe_name>.vmoptions]
    strcpy_s(buffer1, _MAX_PATH, bin_vmoptions);
    char *ideHomeEnd = strrchr(buffer1, '\\') - 4;  // "bin\"
    strcpy_s(ideHomeEnd, _MAX_PATH - (ideHomeEnd - buffer1), ".vmoptions");
    if (LoadVMOptionsFile(buffer1, user_lines)) {
      vmOptionsFile = buffer1;
    }
    else {
      LoadStringA(hInst, IDS_VM_OPTIONS_PATH, buffer1, _MAX_PATH);
      ExpandEnvironmentStringsA(buffer1, buffer2, _MAX_PATH);
      char *exeParentEnd = strrchr(bin_vmoptions, '\\');
      strcat_s(buffer2, exeParentEnd);
      if (LoadVMOptionsFile(buffer2, user_lines)) {
        vmOptionsFile = buffer2;
      }
    }
  }

  if (!user_lines.empty()) {
    if (!lines.empty()) {
      bool (*GC_lookup)(std::string &) = [](std::string &s){
        return strncmp(s.c_str(), "-XX:+Use", 8) == 0 && strcmp(s.c_str() + s.length() - 2, "GC") == 0;
      };
      if (std::find_if(user_lines.begin(), user_lines.end(), GC_lookup) != user_lines.end()) {
        lines.erase(std::remove_if(lines.begin(), lines.end(), GC_lookup), lines.end());
      }
    }
    lines.insert(lines.end(), user_lines.begin(), user_lines.end());
  }

  if (vmOptionsFile != NULL) {
    lines.push_back(std::string("-Djb.vmOptionsFile=") + vmOptionsFile);
  }
  else {
    wchar_t *titleBuf = NULL;
    int len = LoadStringW(hInst, IDS_ERROR_LAUNCHING_APP, (LPWSTR)(&titleBuf), 0);
    if (len != 0) {
      std::wstring title(titleBuf, len);
      MessageBoxW(NULL, L"Cannot find VM options file", title.c_str(), MB_OK);
    }
  }

  AddClassPathOptions(lines);
  AddBootClassPathOptions(lines);
  AddPredefinedVMOptions(lines);

  vmOptionCount = (int)lines.size() + 1;
  vmOptions = (JavaVMOption *)calloc(vmOptionCount, sizeof(JavaVMOption));
  vmOptions[0].optionString = (char *)"exit";
  vmOptions[0].extraInfo = (void *)jniExitHook;
  for (int i = 0; i < lines.size(); i++) {
    vmOptions[i + 1].optionString = _strdup(lines[i].c_str());
    vmOptions[i + 1].extraInfo = NULL;
  }

  return true;
}

bool LoadJVMLibrary()
{
  std::string binDir = std::string(jvmPath) + "\\bin";
  std::string dllName = binDir + "\\server\\jvm.dll";

  // Sometimes the parent process may call SetDllDirectory to change its own context, and this will be inherited by the
  // launcher. In that case, we won't be able to load the libraries from the current directory that is set below. So, to
  // fix such cases, we have to reset the DllDirectory to restore the default DLL loading order.
  SetDllDirectoryW(nullptr);

  // Call SetCurrentDirectory to allow jvm.dll to load the corresponding runtime libraries.
  SetCurrentDirectoryA(binDir.c_str());
  hJVM = LoadLibraryA(dllName.c_str());
  if (hJVM)
  {
    pCreateJavaVM = (JNI_createJavaVM) GetProcAddress(hJVM, "JNI_CreateJavaVM");
  }

  if (!pCreateJavaVM)
  {
    std::string jvmError = "Failed to load JVM DLL ";
    jvmError += dllName.c_str();
    jvmError += "\n"
        "If you already have a JDK installed, define a JAVA_HOME variable in "
        "Computer > System Properties > System Settings > Environment Variables.";
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, jvmError.c_str(), error.c_str(), MB_OK);
    return false;
  }
  return true;
}

static bool IsJBRE(JNIEnv* jenv)
{
  if (!jenv) return false;

  jclass cls = jenv->FindClass("java/lang/System");
  if (!cls) return false;

  jmethodID method = jenv->GetStaticMethodID(cls, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
  if (!method) return false;

  jstring jvendor = (jstring)jenv->CallStaticObjectMethod(cls, method, jenv->NewStringUTF("java.vendor"));
  if (!jvendor) return false;

  const char *cvendor = jenv->GetStringUTFChars(jvendor, NULL);
  const bool isJB = (strstr(cvendor, "JetBrains") != NULL);
  jenv->ReleaseStringUTFChars(jvendor, cvendor);

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
// possible error values:
// JNI_ERR          (-1)  /* unknown error */
// JNI_EDETACHED    (-2)  /* thread detached from the VM */
// JNI_EVERSION     (-3)  /* JNI version error */
// JNI_ENOMEM       (-4)  /* not enough memory */
// JNI_EEXIST       (-5)  /* VM already created */
// JNI_EINVAL       (-6)  /* invalid arguments */
  std::string errorMessage = "";
  if (errorCode == -6)
  {
      errorMessage = "Improperly specified VM option. To fix the problem, edit your JVM options and remove the options that are obsolete or not supported by the current version of the JVM.";
  }
  return errorMessage;
}

static JNIEnv* CreateJVM()
{
  JavaVMInitArgs initArgs;
  initArgs.version = JNI_VERSION_1_2;
  initArgs.options = vmOptions;
  initArgs.nOptions = vmOptionCount;
  initArgs.ignoreUnrecognized = JNI_FALSE;

  JNIEnv* jenv = NULL;
  int result = pCreateJavaVM(&jvm, &jenv, &initArgs);

  for (int i = 1; i < vmOptionCount; i++)
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
        jvmError = "If you already have a JDK installed, define a JAVA_HOME variable in \n";
        jvmError += "Computer > System Properties > System Settings > Environment Variables.\n";
    }

    buf << jvmError;
    buf << "\nFailed to create JVM. ";
    buf << "JVM Path: " << jvmPath;
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, buf.str().c_str(), error.c_str(), MB_OK);
  }

  // Set DPI-awareness here or let JBRE do that.
  if (!IsJBRE(jenv)) SetProcessDPIAwareProperty();

  return (result == JNI_OK ? jenv : NULL);
}

static jobjectArray ArgsToJavaArray(JNIEnv* jenv, std::vector<LPWSTR> args)
{
  jclass stringClass = jenv->FindClass("java/lang/String");
  jobjectArray result = jenv->NewObjectArray(args.size(), stringClass, NULL);
  for (int i = 0; i < args.size(); i++)
  {
     jenv->SetObjectArrayElement(result, i, jenv->NewString((const jchar *)args[i], wcslen(args[i])));
  }
  return result;
}

bool isNumber(std::string line)
{
  char* p;
  strtol(line.c_str(), &p, 10);
  return *p == 0;
}

std::vector<LPWSTR> ParseCommandLine(LPCWSTR commandLine)
{
  int numArgs;
  LPWSTR* argv = CommandLineToArgvW(commandLine, &numArgs);

  // skip process name
  std::vector<LPWSTR> result;
  for (int i = 1; i < numArgs; i++)
  {
    std::wstring arg(argv[i]);
    std::string command(arg.begin(), arg.end());
    // IDEA-230983
    if (command.find_last_of(":") != std::string::npos && command.rfind("jetbrains://", 0) != 0)
    {
      std::string line = command.substr(command.find_last_of(":") + 1);
      if (isNumber(line))
      {
        result.push_back(L"-l");
        int numArgs;
        LPWSTR* lineNumberArg = CommandLineToArgvW(std::wstring(line.begin(), line.end()).c_str(), &numArgs);
        result.push_back(lineNumberArg[0]);
        std::string fileName = command.substr(0, command.find_last_of(":"));
        LPWSTR* fileNameArg = CommandLineToArgvW(std::wstring(fileName.begin(), fileName.end()).c_str(), &numArgs);
        result.push_back(fileNameArg[0]);
        continue;
      }
    }

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

static bool RunMainClass(JNIEnv* jenv, std::vector<LPWSTR> args)
{
  const std::string mainClassName = LoadStdString(IDS_MAIN_CLASS);
  jclass mainClass = jenv->FindClass(mainClassName.c_str());
  if (!mainClass)
  {
    char buf[_MAX_PATH + 256];
    sprintf_s(buf, "Could not find main class %s", mainClassName.c_str());
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, buf, error.c_str(), MB_OK);
    return false;
  }

  jmethodID mainMethod = jenv->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
  if (!mainMethod)
  {
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, "Could not find main method", error.c_str(), MB_OK);
    return false;
  }

  jenv->CallStaticVoidMethod(mainClass, mainMethod, ArgsToJavaArray(jenv, args));
  jthrowable exc = jenv->ExceptionOccurred();
  if (exc)
  {
    std::string error = LoadStdString(IDS_ERROR_LAUNCHING_APP);
    MessageBoxA(NULL, "Error invoking main method", error.c_str(), MB_OK);
  }

  return true;
}

static int CallCommandLineProcessor(JNIEnv* jenv, const std::wstring& curDir, const std::wstring& args)
{
  int exitCode = -1;

  const std::string processorClassName = LoadStdString(IDS_COMMAND_LINE_PROCESSOR_CLASS);
  jclass processorClass = jenv->FindClass(processorClassName.c_str());
  if (processorClass)
  {
    jmethodID processMethodID = jenv->GetStaticMethodID(processorClass, "processWindowsLauncherCommandLine", "(Ljava/lang/String;[Ljava/lang/String;)I");
    if (processMethodID)
    {
      jstring jCurDir = jenv->NewString((const jchar *)curDir.c_str(), curDir.size());
      jobjectArray jArgs = ArgsToJavaArray(jenv, RemovePredefinedArgs(ParseCommandLine(args.c_str())));
      exitCode = jenv->CallStaticIntMethod(processorClass, processMethodID, jCurDir, jArgs);
      jthrowable exc = jenv->ExceptionOccurred();
      if (exc)
      {
        MessageBox(NULL, _T("Error sending command line to existing instance"), _T("Error"), MB_OK);
      }
    }
  }
  return exitCode;
}

DWORD WINAPI SingleInstanceThread(LPVOID args)
{
  JavaVMAttachArgs attachArgs{ JNI_VERSION_1_2,
                               "WinLauncher external command processing thread",
                               NULL };
  JNIEnv *jenv = NULL; // NB: JNIEnv is thread-local, which is why we must obtain our own
  jint rc = jvm->AttachCurrentThread(reinterpret_cast<void**>(&jenv), &attachArgs);
  if (rc != JNI_OK) return 0;

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
      int second_pos = command.find('\n', pos + 1);
      std::wstring curDir = command.substr(0, pos);
      std::wstring args = command.substr(pos + 1, second_pos - pos - 1);
      std::wstring response_id = command.substr(second_pos + 1);

      int exitCode = CallCommandLineProcessor(jenv, curDir, args);

      std::string message = std::to_string(static_cast<long long>(exitCode));
      std::string resultFileName = std::string("IntelliJLauncherResultMapping.") + std::string(response_id.begin(), response_id.end());
      HANDLE hResultFileMapping = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, resultFileName.c_str());
      if (hResultFileMapping)
      {
        void *resultView = MapViewOfFile(hResultFileMapping, FILE_MAP_ALL_ACCESS, 0, 0, 0);
        if (resultView)
        {
          memcpy(resultView, message.c_str(), (message.size() + 1) * sizeof(wchar_t));
          UnmapViewOfFile(resultView);
        }

        std::string eventName = std::string("IntelliJLauncherEvent.") + std::string(response_id.begin(), response_id.end());
        HANDLE hResponseEvent = CreateEventA(NULL, FALSE, FALSE, eventName.c_str());
        SetEvent(hResponseEvent);
        CloseHandle(hResponseEvent);
        CloseHandle(hResultFileMapping);
      }

    }

    UnmapViewOfFile(view);
  }

  jvm->DetachCurrentThread();
  return 0;
}

void SendCommandLineToFirstInstance(int response_id)
{
  wchar_t curDir[_MAX_PATH];
  GetCurrentDirectoryW(_MAX_PATH - 1, curDir);
  std::string resultFileName = std::to_string(static_cast<long long>(response_id));

  std::wstring command(curDir);
  command += _T("\n");
  command += GetCommandLineW();
  command += _T("\n");
  command += std::wstring(resultFileName.begin(), resultFileName.end());

  void *view = MapViewOfFile(hFileMapping, FILE_MAP_ALL_ACCESS, 0, 0, 0);
  if (view)
  {
    memcpy(view, command.c_str(), (command.size() + 1) * sizeof(wchar_t));
    UnmapViewOfFile(view);
  }
  SetEvent(hEvent);
}

int CheckSingleInstance()
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
    // Means we're the first instance
    return -1;
  }
  else
  {
    srand(239);
    int response_id;
    std::string resultFileName;

    // Let's find a vacant result port. It's advised to use different result connections:
    // that's because requests can be blocking (for quite a time) and several ones might exist at once.
    while (true)
    {
      response_id = rand();
      resultFileName = std::string("IntelliJLauncherResultMapping.") + std::to_string(static_cast<long long>(response_id));

      if (!OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, resultFileName.c_str()))
        break;
    }

    // Creating mapping for exitCode transmission
    HANDLE hResultFileMapping = CreateFileMappingA(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, FILE_MAPPING_SIZE, resultFileName.c_str());

    std::string responseEventName = std::string("IntelliJLauncherEvent.") + std::to_string(static_cast<long long>(response_id));
    HANDLE hResponseEvent = CreateEventA(NULL, FALSE, FALSE, responseEventName.c_str());

    SendCommandLineToFirstInstance(response_id);
    CloseHandle(hFileMapping);

    // It is theoretically possible for this code to spin forever in a loop.
    //
    // There's a race condition when the process we talked to in SendCommandLineToFirstInstance was terminated, another
    // one started, took over the file mapping, but has no idea about our command (because we only send it once).
    //
    // For now, this problem is unresolved, though it should very rarely happen in practice.
    const DWORD waitTimeoutMs = 1000;
    while (WaitForSingleObject(hResponseEvent, waitTimeoutMs) == WAIT_TIMEOUT)
    {
      // Check if the file mapping still exists outside the current process:
      hFileMapping = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, mappingName.c_str());
      if (!hFileMapping)
      {
        // Means the mapping was abandoned by the initial process we observed. So, we should take over.
        hFileMapping = CreateFileMappingA(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, FILE_MAPPING_SIZE,
          mappingName.c_str());
        CloseHandle(hResultFileMapping);
        CloseHandle(hResponseEvent);
        return -1;
      }

      // Ok, the mapping still exists, so the process is still alive. Proceed to spin.
      CloseHandle(hFileMapping);
    }

    CloseHandle(hEvent);
    CloseHandle(hResponseEvent);

    // Read the exitCode
    wchar_t *view = static_cast<wchar_t *>(MapViewOfFile(hResultFileMapping, FILE_MAP_ALL_ACCESS, 0, 0, 0));
    int exitCode;
    if (view)
    {
      exitCode = (int)wcstol(view, NULL, 10);
      UnmapViewOfFile(view);
    }
    else
    {
      exitCode = 1;
    }

    CloseHandle(hResultFileMapping);
    return exitCode;
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

std::wstring GetCurrentDirectoryAsString()
{
  std::vector<wchar_t> buffer(_MAX_PATH);
  DWORD sizeWithoutTerminatingZero = GetCurrentDirectoryW(buffer.size(), buffer.data());
  if (sizeWithoutTerminatingZero >= buffer.size())
  {
    buffer.resize(sizeWithoutTerminatingZero + 1);
    sizeWithoutTerminatingZero = GetCurrentDirectoryW(buffer.size(), buffer.data());
  }

  return std::wstring(buffer.data(), sizeWithoutTerminatingZero);
}

static void SetPathVariable(const wchar_t *varName, REFKNOWNFOLDERID rfId)
{
  wchar_t env_var_buffer[1];
  if (GetEnvironmentVariableW(varName, env_var_buffer, 1) == 0 && GetLastError() == ERROR_ENVVAR_NOT_FOUND)
  {
    wchar_t *path = NULL;
    if (SHGetKnownFolderPath(rfId, KF_FLAG_DONT_VERIFY, NULL, &path) == S_OK)
    {
      SetEnvironmentVariableW(varName, path);
      CoTaskMemFree(path);
    }
  }
}

void PrintUsage()
{
  char fullPath[_MAX_PATH];
  GetModuleFileNameA(NULL, fullPath, _MAX_PATH);
  std::string::size_type pos = std::string(fullPath).find_last_of("\\/");
  std::string fileName = std::string(fullPath).substr(pos+1);

  std::stringstream buf;
  buf << "Usage:\n   ";
  buf << fileName + " -h | -? | --help\n   ";
  buf << fileName + " [project_dir]\n   ";
  buf << fileName + " [-l|--line line] [project_dir|--temp-project] file[:line]\n   ";
  buf << fileName + " diff <left> <right>\n   ";
  buf << fileName + " merge <local> <remote> [base] <merged>";

  MessageBoxA(NULL, buf.str().c_str(), "Command-line Options", MB_OK);
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

  for (int i = 1; i < __argc; i++)
  {
    if (wcscmp(L"-h", __wargv[i]) == 0 || wcscmp(L"-?", __wargv[i]) == 0 || wcscmp(L"--help", __wargv[i]) == 0)
    {
      PrintUsage();
      return 0;
    }
  }

  // ensures path variables are defined
  SetPathVariable(L"APPDATA", FOLDERID_RoamingAppData);
  SetPathVariable(L"LOCALAPPDATA", FOLDERID_LocalAppData);

  //it's OK to return 0 here, because the control is transferred to the first instance
  int exitCode = CheckSingleInstance();
  if (exitCode != -1) return exitCode;

  // Read current directory and pass it to JVM through environment variable. The real current directory will be changed
  // in LoadJVMLibrary.
  std::wstring currentDirectory = GetCurrentDirectoryAsString();
  SetEnvironmentVariableW(L"IDEA_INITIAL_DIRECTORY", currentDirectory.c_str());

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
  JNIEnv* jenv = CreateJVM();
  if (jenv == NULL) return 1;

  hSingleInstanceWatcherThread = CreateThread(NULL, 0, SingleInstanceThread, NULL, 0, NULL);

  if (!RunMainClass(jenv, args)) return 1;

  jvm->DestroyJavaVM();

  terminating = true;
  SetEvent(hEvent);
  WaitForSingleObject(hSingleInstanceWatcherThread, INFINITE);
  CloseHandle(hEvent);
  CloseHandle(hFileMapping);

  return hookExitCode;
}
