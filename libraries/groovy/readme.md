# Groovy library modules

This directory contains product wrapper modules for Groovy Maven artifacts.
They were introduced when the old platform-level `dist.all/lib/groovy.jar` bundle was removed.
That old platform jar blindly bundled several Groovy artifacts, so those classes were available from the platform classloader even when no module or plugin declared a direct dependency.

The original platform bundle included:

- `org.codehaus.groovy:groovy`
- `org.codehaus.groovy:groovy-jsr223`
- `org.codehaus.groovy:groovy-json`
- `org.codehaus.groovy:groovy-templates`
- `org.codehaus.groovy:groovy-xml`

Current direct usage is narrower:

- `intellij.libraries.groovy` is actively used by Groovy support and optional integrations that evaluate Groovy code.
- `intellij.libraries.groovy.ant` is still referenced by Gradle packaging/dependencies.
- `intellij.libraries.groovy.json`, `intellij.libraries.groovy.jsr223`, `intellij.libraries.groovy.templates`, and `intellij.libraries.groovy.xml` currently have no direct IDE/module consumers outside their own wrapper files and generated metadata.

Before removing the unused wrappers, account for the compatibility risk: external scripts, SSR Groovy scripts, reflective lookups, or legacy runtime paths may have relied on these jars being accidentally present through the old platform bundle.

Decision pending: either remove the unused wrappers after validation, or deliberately bundle selected artifacts from the Groovy plugin if preserving that accidental runtime availability is required.
