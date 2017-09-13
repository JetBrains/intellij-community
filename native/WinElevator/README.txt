With UAC enabled even administrator has limited access token and can't modify some folders like "Program Files".
The only official way to elevate token for process is to launch app as elevated using shell api so user will have chance to accept it. 
Since it may lead to security risks it is not recomended to run whole app as elevated, but run only certain tools instead.

This app consists of 2 apps:
* launcher: accepts command line to run as elevated and launches "elevator" using shell api
* elevator: has "UAC execution level" set "administrator" in its manifest, so UAC is displayed. It then runs provided command line.

Since elevator is launched with elevated priviliges, it has separate console in conhost (technically it is not child of launcher but of AppInfo instead), 
so there is some machinery to connect elevated process to console and its pipes.

Launcher provides its pid to elevator and elevator attaches to its console.

But if std(out|err|in) are redirected to files or pipes, attaching to console is not enough.
In this case launcher creates named pipes, elevator connects to them and provides their handlers as handlers for newly created process.
Launcher then creates threads to read/write them to console.

-------
How to build.
You may open .sln from Visual Studio or use msbuild from VS command prompt:
 msbuild Elevator.sln /p:Configuration=release


Building and signing on TC:
To sign binary you need to build and sign it on TC.
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
Both tools report errors as error codes, stderr and event log under "Application".
No event ids are registered, but you can check eventId and find appropriate ReportEvent call.