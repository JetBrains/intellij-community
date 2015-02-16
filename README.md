# IntelliJ IDEA Community Edition
### Building and Running from the IDE
To develop IntelliJ IDEA, you can use either IntelliJ IDEA Community Edition or IntelliJ IDEA Ultimate. To build and run the code:
* Make sure you have the **Groovy** plugin enabled. Parts of IntelliJ IDEA are written in Groovy, and you will get compilation errors if you don't have the plugin enabled.
* Make sure you have the **UI Designer** plugin enabled. Most of IntelliJ IDEA's UI is built using the **UI Designer**, and the version you build will not run correctly if you don't have the plugin enabled.
* Open the directory with the source code as a [directory-based project](http://www.jetbrains.com/idea/webhelp/project.html)
* Configure a JSDK named "**IDEA jdk**" (case sensitive), pointing to an installation of JDK 1.6
* Add **lib\tools.jar** from the JDK installation directory to the classpath of IDEA JDK
* Use Build | Make Project to build the code.
* To run the code, use the provided shared run configuration "**IDEA**".

You can find other useful information at [http://www.jetbrains.org](http://www.jetbrains.org)