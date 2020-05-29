# Android Studio Release Process

 1. For every build to be released (canary, beta, rc, or final),
    on the release branch (e.g. studio-1.4-release),
    make sure the version number is correct in
    ./adt-branding/src/idea/AndroidStudioApplicationInfo.xml

    Example:

    ```
      <version major="1" minor="4" micro="0" patch="7" full="{0}.{1} RC 1" eap="false" />
                     ~~~       ~~~       ~~~       ~~~      ~~~~~~~~~~~~~~
    ```

 2. Also make sure that the `eap=` flag in the same file is correct.
    It should be `true` for canary and beta builds, `false` for rc and final:

    ```
      <version major="1" minor="4" micro="0" patch="7" full="{0}.{1} RC 1" eap="false" />
                                                                           ~~~~~~~~~~~
    ```
    Among other things, in `VmOptionsGenerator.groovy` this causes the `isEAP`
    conditional to disable assertions.

 1. Replace `dev` with the appropriate release designator in
    ../buildSrc/base/version.properties:

    ```
    baseVersion = 24.4.0-rc01
    buildVersion = 1.4.0-rc01
                         ~~~~
    ```

--------------------------------------------------------------------------------
When a new dev branch (like studio-3.1-dev) is created, update studio-master-dev:

 1. Make sure the major/minor version is correctly encoded in the build number
    listed in build.txt file. It is the second number from the end. If major
    version is X and minor version is Y, the number is simply XY. For example,
    31, 32, 33 for 3.1, 3.2, 3.3 respectively.

    ```
    181.2784.17.32.SNAPSHOT
                ~~
    ```

 4. Make sure the version number is correct in
    ../adt/idea/native/installer/win/setup_android_studio.nsi

    ```
    !define VERSION_MAJOR 3
    !define VERSION_MINOR 2
    ```

 1. Update the version numbers in ../buildSrc/base/version.properties:

    ```
    baseVersion = 26.2.0-dev
    buildVersion = 3.2.0-dev
                  ~~~~
    ```

 1. Add an entry for the new version in [Kotlin compatibility metadata](https://dl.google.com/android/studio/plugins/compatibility.xml)
--------------------------------------------------------------------------------
Before the first release candidate, update the dev branch (like studio-1.4-dev):

 1. Turn off null checking.

    Edit .idea/compiler.xml and make sure null assertions are disabled by
    adding the following line:

    ```diff
         <option name="BUILD_PROCESS_HEAP_SIZE" value="1100" />
    +    <addNotNullAssertions enabled="false" />
         <excludeFromCompile>
    ```

    (We don't leave it in with enabled="true" because the IDE will automatically
    remove it and then leave the .idea/compiler.xml file in an edited state
    for all developers who open the project.)

    Edit .idea/kotlinc.xml and disable assertions in the "additionalArguments"
    option in "KotlinCompilerSettings":

    ```diff
       <component name="KotlinCompilerSettings">
    -    <option name="additionalArguments" value="-version -Xjvm-default=enable -Xstrict-java-nullability-assertions" />
    +    <option name="additionalArguments" value="-version -Xjvm-default=enable -Xno-param-assertions -Xno-call-assertions -Xno-receiver-assertions" />
       </component>
    ```

--------------------------------------------------------------------------------
For stable, RC, beta builds :

 1. Ensure that the default update channel is set to RELEASE for stable build,
    or BETA for beta or RC builds. This is so that new users of beta, RC, stable
    builds are not prompted to upgrade to canary by default.

    Edit platform/platform-impl/src/com/intellij/openapi/updateSettings/impl/UpdateOptions.kt
    to make sure `updateChannelType` is set to:
    * `ChannelStatus.RELEASE.code` for stable releases, or
    * `ChannelStatus.BETA.code` for beta, RC releases, or
    * `ChannelStatus.EAP.code` otherwise.

--------------------------------------------------------------------------------
For AOSP push:

 1. Update the build scripts such that they no longer reference any of
    the closed source plugins such as the C++ support; this means removing
    the vendor/ plugin references from .idea/modules.xml, community-main.xml,
    build/groovy/org/jetbrains/intellij/build/AndroidStudioProperties.groovy,
    .idea/runConfigurations/OneStudio.xml and the reference in .idea/ant.xml
    to vendor/google3.

    There are many other smaller tasks to handle as well - updating vcs.xml
    to not reference unavailable git repositories, etc etc.

    There are three basic tasks:
    (1) Build the IDE from the command line, and copy the profiler prebuilts

        $ cp tools/idea/out/studio/dist.all/plugins/android/lib/studio-proto.jar \
             tools/adt/idea/android/lib/

        $ cp tools/idea/out/studio/dist.all/plugins/android/lib/transport_java_proto.jar \
             tools/adt/idea/android/lib/

        $ cp tools/idea/out/studio/dist.all/plugins/android/lib/studio-grpc.jar \
             tools/adt/idea/android/lib/

        $ cp tools/idea/out/studio/dist.all/plugins/android/lib/perfetto-protos.jar \
             tools/adt/idea/android/lib/

        $ cp-recursive tools/idea/out/studio/dist.all/plugins/android/resources/ \
                prebuilts/tools/common/profiler/$VERSION/

    Also add in a README and license file in the new profiler prebuilts folder.

    Then remove the references from build.xml and .idea/build.xml to
    the adt build.xml file which would run profiler prebuild steps.

    (2) Open the project in IntelliJ, and fix all warnings in the project
        structure dialog, then make sure the project builds and runs; for
        this you may have to fix up source code in case there are any
        dependencies on closed source code that shouldn't be there.
        For example, right now the appindexing plugin depends on the
        url-assistant

    (3) Make the build_studio.sh script compile. This involves removing
        the various cidr plugins and closed source plugins, as well as
        removing compilation/copy tasks for the gradle offline repository,
        lldb, etc. Also add a copy task to place the profiler prebuilts
        into place.


    (4) Ensure that the branch names for the tools/idea and tools/base projects
        are sensible:

    ```diff
    diff --git a/.idea/.name b/.idea/.name
    index 310ac3d20a3..8a1a9797418 100644
    --- a/.idea/.name
    +++ b/.idea/.name
    @@ -1 +1 @@
    -Android Studio (studio-master-dev)
    \ No newline at end of file
    +Android Studio 3.1
    \ No newline at end of file
    ```

 Relevant CLs from the 3.1 push:
 Change-Id: I8ca078c08a4fff0a6ebeae8c6ba7a6fa55bec490
 Change-Id: I15c8e3ce46099d54e77c17fff0f0d42d11238e3b
 Change-Id: Ia656d3e023d9887da7aa0486ca934388e75436db

 Relevant CLs from the 3.0 AOSP push:
 https://android-review.googlesource.com/#/q/topic:studio-30

 Relevant CLs from the 2.3 AOSP push (though they'll need to be adjusted to
 account for the big build script changes in 2.4) :
 Change-Id: I364ee67262524aa27f71831e6ed01164826e54ad
 Change-Id: I9298c7319ce55fb64c29e38636808e57f2a84209
 Change-Id: Ic60a95a21ea0e483d82f6013539d112b89a7d0c0 (AOSP)
 Change-Id: I42bcad10589b8172a46a3f74aa1e7a5826ea52fc (AOSP)

 1. Remove all references to `tools/vendor/google` from
 `tools/base/bazel/toplevel.WORKSPACE`:

```
--- a/bazel/toplevel.WORKSPACE
+++ b/bazel/toplevel.WORKSPACE
@@ -1,13 +1,6 @@
 load("//tools/base/bazel:repositories.bzl", "setup_external_repositories")
 setup_external_repositories()

-local_repository(
-      name = "blaze",
-      path = "tools/vendor/google3/blaze",
-)
-load("@blaze//:binds.bzl", "blaze_binds")
-blaze_binds()
-
 http_archive(
   name = "bazel_toolchains",
   urls = [
```
