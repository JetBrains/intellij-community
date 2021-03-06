# WinShellIntegration
Library provides the features allow integrate your application into Windows Shell:
* Managing AppUserModelID property of the current process
* Jump lists support

This project based on the [origin jumplistbridge project](https://github.com/JetBrains/intellij-community/tree/4635352640ed54ef9379082171f33837d099ebb8/native/jumplistbridge)
by Denis Fokin.

Windows 8 and higher are supported.

## Build dependencies
* C++17-compatible compiler;
* [CMake](https://cmake.org/download/) >= `v3.15` (Note: it is already shipped with CLion);
* [Optional: only for building [winshellintegrationbridge](src/winshellintegrationbridge) target]:
  An implementation of Java SE 11 or newer. It's highly recommended to use the JDK you are using to build IDEA.

## Build
Just use CMake normally.

If you want to build [winshellintegrationbridge](src/winshellintegrationbridge) target too
you have to set the variable `JDK_PATH` to the path of JDK which will be used for build.
For example if your `javac.exe` is located at `C:\Soft\jdk\bin\javac.exe` you should set the variable like
`-DJDK_PATH="C:\Soft\jdk"`.

## Integration with CLion
1. Configure the toolchain(s) (if you need help, please check [this guide](https://www.jetbrains.com/help/clion/quick-tutorial-on-configuring-clion-on-windows.html));
2. Configure CMake profiles (if you need help, please check [this guide](https://www.jetbrains.com/help/clion/cmake-profile.html)).
   Don't forget to pass `JDK_PATH` variable (via `CMake options` field) if you want to build `winshellintegrationbridge` target.
   Also you can set the root of each profile to [build](build) dir: it already has `.gitignore` file
    that will exclude everything under it.

## Current binaries in repository
Current version of IntelliJ Platform uses binaries located in:
* x32: [bin/win/WinShellIntegrationBridge.dll](../../bin/win/WinShellIntegrationBridge.dll);
* x64: [bin/win/WinShellIntegrationBridge64.dll](../../bin/win/WinShellIntegrationBridge64.dll).

These binaries are built in the following environment:
* Windows 10 build 19041;
* Visual Studio 2017 Community MSVC 19.16.27043.0, Windows SDK v8.0 6.2.9200.16384;
* [Amazon Corretto JDK 11.0.8.10.1](https://corretto.aws/downloads/resources/11.0.8.10.1/amazon-corretto-11.0.8.10.1-windows-x64.msi).

And using the following commands **ran at Visual Studio Developer Command Prompt**:
```bat
cd build

cmake -E rm -rf "x32"
cmake -G"Visual Studio 15 2017" -A Win32 -DCMAKE_SYSTEM_VERSION=8.0 -DJDK_PATH="%JDK_11%" -S ".." -B "x32"
cmake --build "x32" --config RelWithDebInfo

cmake -E rm -rf "x64"
cmake -G"Visual Studio 15 2017" -A x64 -DCMAKE_SYSTEM_VERSION=8.0 -DJDK_PATH="%JDK_11_x64%" -S ".." -B "x64"
cmake --build "x64" --config RelWithDebInfo
```
