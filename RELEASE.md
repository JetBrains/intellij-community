# Android Studio Release Process

These changes (except for step 1) are typically only done in a
*release* branch (e.g. studio-1.4-release), and only for release
candidate and stable builds (not the release branch for milestones and
betas.)

 1. Make sure the version number is correct in
    ../adt/idea/adt-branding/src/idea/AndroidStudioApplicationInfo.xml

    Example:

    ```
      <version major="1" minor="4" micro="0" patch="7" full="{0}.{1} RC 1" eap="false" />
                     ~~~       ~~~       ~~~       ~~~      ~~~~~~~~~~~~~~
    ```

 2. Also make sure that the eap= flag in the same file is correct.

    ```
     <version major="1" minor="4" micro="0" patch="7" full="{0}.{1} RC 1" eap="false" />
                                                                          ~~~~~~~~~~~
    ```

 3. *If necessary*, update the "selector name"; this is the name used
    in settings directories (such as ~/.AndroidStudioPreview1.6 on
    Linux, or ~/Library/Preferences/AndroidStudioPreview1.6 on OSX,
    etc.

    This directory name used to be hardcoded, but is now derived from
    the version numbers specified in the branding files. If you want
    to tweak it, edit build/scripts/studio_properties.gant and tweak
    the string returned by the systemSelector() method.

    The convention we have been using is the following for previews:
        `AndroidStudioPreviewX.Y`
    and for release builds:
        `AndroidStudioX.Y`.

 4. Update the settings importer.

    Edit
    `platform/platform-impl/src/com/intellij/openapi/application/ConfigImportHelper.java`
    such that it imports from the previous few versions (e.g. previous stable
    versions, as well as most recent preview.)

    Example CL: https://android-review.googlesource.com/#/c/161783/


 5. Make sure assertions are turned off.

    This is controlled by `build/scripts/utils.gant`:

    ```
    binding.setVariable("common_vmoptions", "-XX:+UseConcMarkSweepGC -XX:SoftRefLRUPolicyMSPerMB=50 -da " + ...
                                                                                                   ~~~~~
    ```

    This should be set to -da in production builds.

 6. Make sure -OmitStackTraceInFastThrow is removed.

    This is controlled by `build/scripts/utils.gant`:

    ```
    binding.setVariable("common_vmoptions", "... -XX:SoftRefLRUPolicyMSPerMB=50 -ea -XX:-OmitStackTraceInFastThrow " + ...
                                                                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    ```

 7. Turn off null checking.

    Edit .idea/compiler.xml and make sure null assertions are disabled by
    adding the following line:

    ```
         <option name="DEFAULT_COMPILER" value="Javac" />
    +    <addNotNullAssertions enabled="false" />
         <excludeFromCompile>
    ```

    (We don't leave it in with enabled="true" because the IDE will automatically
    remove it and then leave the .idea/compiler.xml file in an edited state
    for all developers who open the project.)

 8. Turn off CLASS retention in
    platform/annotations/src/org/jetbrains/annotations

    ```
    --- a/platform/annotations/src/org/jetbrains/annotations/NotNull.java
    +++ b/platform/annotations/src/org/jetbrains/annotations/NotNull.java
    @@ -27,7 +27,7 @@ import java.lang.annotation.*;
      * @author max
      */
     @Documented
    -@Retention(RetentionPolicy.CLASS)
    +@Retention(RetentionPolicy.SOURCE)
     @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
     public @interface NotNull {
       /**
    ```

 9. Use data-binding prebuilt jar instead of depending on modules.

    Build Android Studio and copy the data-binding jar from
    tools/idea/out/dist.all.ce/plugins/android/lib/data-binding.jar to tools/adt/idea/android/lib.
    Edit the tools/idea/build/scripts/layouts.gant file to stop building
    the data-binding modules.

    ```
    --- a/build/scripts/layouts.gant
    +++ b/build/scripts/layouts.gant
    @@ -815,11 +815,6 @@ def layoutAndroid(String androidHome, String androidToolsBaseHome) {
           jar("android-rt.jar") {
             module("android-rt")
           }
    -      jar("data-binding.jar") {
    -        module("db-baseLibrary")
    -        module("db-compilerCommon")
    -        module("db-compiler")
    -      }

           jar("common.jar") {
             module("common")
    ```

    Also edit the tools/adt/idea/android/android.iml file to depend on the prebuilt instead of the module.
    ```
    --- a/android/android.iml
    +++ b/android/android.iml
    @@ -102,6 +102,14 @@
         <orderEntry type="module" module-name="instant-run-client" />
         <orderEntry type="module" module-name="instant-run-common" />
         <orderEntry type="library" name="jna" level="project" />
    -    <orderEntry type="module" module-name="db-compiler" />
    +    <orderEntry type="module-library">
    +      <library>
    +        <CLASSES>
    +          <root url="jar://$MODULE_DIR$/lib/data-binding.jar!/" />
    +        </CLASSES>
    +        <JAVADOC />
    +        <SOURCES />
    +      </library>
    +    </orderEntry>
       </component>
     </module>
    \ No newline at end of file
    ```

10. Ensure that the default Update channel (for new users downloading this build)
    is set to stable, not something else.

    Edit UpdateSettings.State.UPDATE_CHANNEL_TYPE and make sure it's set
    to ChannelStatus.RELEASE.getCode();
