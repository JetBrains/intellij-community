Launcher and elevator:

With UAC enabled, even an administrator has a limited access token and can't modify some folders like "Program Files".
The only official way to elevate token for process is to launch app as elevated using shell api, so users will have a chance to accept it.
Since it may lead to security risks, it is not recommended to run whole app as elevated, but run only certain tools instead.

This app consists of 2 apps:
* launcher: accepts command line to run as elevated and launches "elevator" using shell api
* elevator: has "UAC execution level" set to "administrator" in its manifest, so UAC is displayed. It then runs provided command line.

Since the elevator is launched with elevated privileges, it has a separate console in conhost (technically it is not child of launcher but of AppInfo instead),
so there is some machinery to connect the elevated process to the console and its pipes.

Launcher provides its pid to the elevator, and the elevator attaches to its console.

But if std(out|err|in) are redirected to files or pipes, attaching to a console is not enough.
In this case, the launcher creates named pipes, the elevator connects to them and provides their handlers as handlers for the newly created process.
The launcher then creates threads to read/write them to the console.


-------
How to build.

CMake: 3.16 or newer
ToolChain: VisualStudio 2017
SDK: 8.1 (for "launcher and elevator")

```
if exist build rmdir /s /q build
mkdir build && cd build

cmake -F "Visual Studio 15 2017" -T v141 -A Win32 -DCMAKE_SYSTEM_VERSION="8.1" ..
cmake --build . --config RelWithDebInfo -- -clp:ShowCommandLine

```

Building and signing on TeamCity:
To sign binary you need to build and sign it on TeamCity.
Run https://buildserver.labs.intellij.net/viewType.html?buildTypeId=ijplatform_master_Idea_NativeHelpers_WindowsFileWatcher
Download artifact and store in VCS

-------
How to test.
From unelevated command.com run commands and click "yes"

Testing console
> launcher.exe %ComSpec%
You should be taken to admin console (i.e. has write access to c:\windows\)

Testing output
> echo spam | launcher.exe python.exe -c "import sys;  sys.stderr.write('err'); print(sys.stdin.read()); " > out.txt 2> err.txt
Check you got "spam" in out.txt and "err" in "err.txt"

Ensure permissions
> launcher.exe python.exe -c "print(open('c:\\windows\\eggs.txt', 'w'))"
Check no error

--------
How to check errors.
Tool reports errors as error codes, stderr and event log under "Application".
No event ids are registered, but you can check eventId and find an appropriate ReportEvent call.
