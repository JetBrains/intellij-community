AppX apps are installed in "C:\Program Files\WindowsApps\".
You can't run them directly. Instead, you must use reparse point from
"%LOCALAPPDATA%\Microsoft\WindowsApps" (thi folder is under the %PATH%)

Reparse point is the special structure on NTFS level that stores "reparse tag" (type) and some type-specific data.
When user access such files, Windows redirects her to appropriate target.
So, files in "%LOCALAPPDATA%\Microsoft\WindowsApps" are reparse points to AppX apps, and AppX can only be launched via them.

But for Python there can be reparse point that points to Windows store, so Store is opened when Python is not installed.
There is no official way to tell if "python.exe" there points to AppX python or AppX "Windows Store".

MS provides API (via DeviceIOControl) to read reparse point structure.
There is also raprse point tag for "AppX link" in SDK.
Reparse data is undocumented, but it is just a array of wide chars with some unprintable chars at the beginning.

This tool reads reparse point info and tries to fetch AppX name, so we can see if it points to Store or not.
See https://youtrack.jetbrains.com/issue/PY-43082#focus=Comments-27-4224605.0-0
