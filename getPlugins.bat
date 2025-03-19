@echo off

:ParseArgs
if "%~1" == "--shallow" (
    call git clone git://git.jetbrains.org/idea/android.git android --depth 1
) else (
    call git clone git://git.jetbrains.org/idea/android.git android
)
