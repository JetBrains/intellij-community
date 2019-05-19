@rem Sign up to Bintray and locate your API Key under Edit Your Profile -> API Key
@rem upload.bat <user> <key> <package name> <version> <filepath without spaces>
@rem where <package name> is one of: android-sdk, android-sdk-kotlin-plugin, android-sdk-offline-repo
@echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
"%DIRNAME%/../gradlew.bat" -Duser="%1" -Dkey="%2" -Dpkg="%3" -Dversion="%4" -Dpath="%5" --no-daemon --stacktrace --project-dir=%DIRNAME% bintrayUpload