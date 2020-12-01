# WinShellIntegration

This project based on the [origin jumplistbridge project](https://github.com/JetBrains/intellij-community/tree/4635352640ed54ef9379082171f33837d099ebb8/native/jumplistbridge)
by Denis Fokin.

TODO: description

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
TODO: description

## Current binaries in repository
Current version of IntelliJ Platform uses binaries located in:
* x32: [bin/win/winshellintegrationbridge.dll](../../bin/win/winshellintegrationbridge.dll)
* x64: [bin/win/winshellintegrationbridge64.dll](../../bin/win/winshellintegrationbridge64.dll)

These binaries are built in the following environment:
* Windows 8.1, release 6.3.9600, with latest system updates;
* Visual Studio 2017 Community v15.9.29, Windows 8.1 SDK;
* [CMake 3.15](https://cmake.org/files/v3.15/cmake-3.15.0-win64-x64.msi);
* [Amazon Corretto JDK 11.0.8.10.1](https://corretto.aws/downloads/resources/11.0.8.10.1/amazon-corretto-11.0.8.10.1-windows-x64.msi).

And using the following commands **ran at Visual Studio Developer Command Prompt**:
```bat
cd build
cmd /C "rmdir /S /Q x32 & mkdir x32 && cd x32 && cmake -G"Visual Studio 15 2017" -DJDK_PATH="C:\Program Files\Amazon Corretto\jdk11.0.8_10" "..\.." && cmake --build . --config RelWithDebInfo"
cmd /C "rmdir /S /Q x64 & mkdir x64 && cd x64 && cmake -G"Visual Studio 15 2017 Win64" -DJDK_PATH="C:\Program Files\Amazon Corretto\jdk11.0.8_10" "..\.." && cmake --build . --config RelWithDebInfo"
```
