package org.jetbrains.jewel

import java.io.InputStream

class SimpleResourceLoader(private val classLoader: ClassLoader) : JewelResourceLoader(), ClassLoaderProvider {

    override val classLoaders
        get() = listOf(javaClass.classLoader, classLoader)

    override fun load(resourcePath: String): InputStream {
        val path = resourcePath.removePrefix("/")
        val parentClassLoader =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .callerClass
                .classLoader
        val resource = loadResourceOrNull(path, classLoaders + parentClassLoader)

        return requireNotNull(resource) { "Resource '$resourcePath' not found (tried loading: '$path')" }
    }
}
