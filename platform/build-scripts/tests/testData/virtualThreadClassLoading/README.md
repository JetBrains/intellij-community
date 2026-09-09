# Concurrent class loading from a JAR

This standalone Java test exercises concurrent explicit and implicit class loading from a multi-release JAR.
It uses the standard `URLClassLoader`, without overrides or custom loading locks.
It needs no IntelliJ classes, Kotlin, JUnit, or native test library.

## Compile

Copy `JarClassLoading.java` to an empty directory and compile it with JDK 21 or later:

```sh
mkdir -p classes
"$JAVA_HOME/bin/javac" --release 21 -Xlint:all -Werror -d classes JarClassLoading.java
```

Use JDK 24 or later for the runtime comparisons below.
Earlier releases also pin virtual threads at ordinary synchronized blocks.
Use the same compiled files for every comparison.

## Reproduce

```sh
"$JAVA_HOME/bin/java" -cp classes JarClassLoading 60 load-class virtual
```

Each round creates a fresh `URLClassLoader` for a generated multi-release JAR.
The test starts 64 workers. It does not hold them at a barrier or insert delays into loading.
Half call `loader.loadClass(name)`. The other half execute `new PayloadNNN()` through preloaded trigger classes.
Every worker loads one class. No worker waits for another worker or acquires an application lock during loading.
The payloads have no static initializers.

The test uses two carriers by default.
To change the count, set both scheduler properties to the same positive value:

```sh
"$JAVA_HOME/bin/java" \
  -Djdk.virtualThreadScheduler.parallelism=4 \
  -Djdk.virtualThreadScheduler.maxPoolSize=4 \
  -cp classes JarClassLoading 60
```

## Compare the workaround and controls

```sh
"$JAVA_HOME/bin/java" -cp classes JarClassLoading 60 for-name virtual
"$JAVA_HOME/bin/java" -cp classes JarClassLoading 60 load-class platform
"$JAVA_HOME/bin/java" -cp classes JarClassLoading 60 implicit-only virtual
"$JAVA_HOME/bin/java" -cp classes JarClassLoading 60 explicit-only virtual
```

`for-name` replaces only the explicit call with `Class.forName(name, false, loader)`.
The implicit loads, JAR contents, class loaders, and worker counts stay unchanged.
The other modes change the thread kind or avoid mixing the two loading paths.

## Run longer

Arguments have this order:

```text
JarClassLoading [seconds=60] [load-class|for-name|implicit-only|explicit-only] [virtual|platform] [timeoutSeconds=10]
```

Run the workaround for one day:

```sh
"$JAVA_HOME/bin/java" -cp classes JarClassLoading 86400 for-name virtual 30
```

Use `0` seconds for continuous execution. Progress appears every ten seconds.
The timeout bounds each wait for worker completion.
Completed rounds close their loaders and do not retain their workers.

## Read the result

`PASS` and exit code 0 mean every round completed during the requested duration.
A worker failure or timeout gives a nonzero exit code.
A timeout writes a full thread dump and prints the relevant worker stacks.
The failure output names a temporary directory with the JAR and thread dump.
Keep these artifacts and the complete output, including the runtime version and mode.
Workers are daemon threads. The failure path does not wait for them or close a loader that might hold a blocked lock.

This test replaces the earlier custom-loader fixture, which parked on a latch while holding a loading monitor.
The new fixture uses JDK loading locks and JAR access directly.
It does not replay the complete TeamCity workload or establish the scope of a particular JDK issue.
Use the packaging suite to validate the production change as well.
