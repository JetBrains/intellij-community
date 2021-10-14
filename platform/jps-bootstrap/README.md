# jps-bootstrap

## Previous way of running build scripts

Locally: run *.gant from IDEA or via gant.xml

On buildserver: gant.xml

Downsides:

 * Supports only groovy code. We're tired of groovy.
 * Uses binary dependencies for JPS & Utils, which defies the monorepo approach and also creates duplicate classes in IDEA project
 * Hard to generate and process build output.
On buildserver it's not easy to output something, the output is intercepted and processed by both TeamCity code and ant

## Running build scripts via jps-bootstrap

jps-bootstrap: a small utility that loads JPS project, compiles it, and runs any class from it

Locally:
 * just run CLASS_NAME from IDEA
 * run any main class (written in Java/Kotlin/Groovy) from intellij project via\
`./jps-bootstrap.cmd MODULE_NAME CLASS_NAME ARGS`
 
On buildserver:

 * Separate build compiles jps-bootstrap (only when jps-bootstrap sources are changed)
 * jps-bootstrap is used to run Compile Inc (it compiles build scripts on the fly)
 * On any other build, jps-bootstrap uses already compiled classes from Compile Inc