// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.SystemProperties

class JavaParametersBuilder(private val project: Project) {
    private var mainClassName: String? = null
    private var sdk: Sdk? = null

    fun build(): JavaParameters = JavaParameters().apply {
        mainClass = mainClassName
        jdk = sdk
            ?: ProjectRootManager.getInstance(project).projectSdk
                    ?: SimpleJavaSdkType().createJdk(
                "tmp",
                SystemProperties.getJavaHome()
            )
        setShortenCommandLine(getDefaultShortenCommandLineMethod(jdk?.homePath), project)
    }

    /**
     * This method is partially copied from IDEA sources but doesn't check presence of dynamic.classpath property
     * because we want to shorten command line for scratches and repl anyway
     * @see [ShortenCommandLine.getDefaultMethod]
     */
    private fun getDefaultShortenCommandLineMethod(rootPath: String?): ShortenCommandLine {
        return if (rootPath != null && JdkUtil.isModularRuntime(rootPath)) {
            ShortenCommandLine.ARGS_FILE
        } else if (JdkUtil.useClasspathJar()) {
            ShortenCommandLine.MANIFEST
        } else {
            ShortenCommandLine.CLASSPATH_FILE
        }
    }

    fun withMainClassName(name: String?): JavaParametersBuilder {
        mainClassName = name
        return this
    }

    fun withSdkFrom(module: Module?): JavaParametersBuilder {
        if (module == null) return this

        val sdk = ModuleRootManager.getInstance(module).sdk ?: return this
        if (sdk.sdkType is JavaSdkType) {
            this.sdk = sdk
        }

        return this
    }

    companion object {
        fun getModuleDependencies(module: Module): List<String> =
            OrderEnumerator.orderEntries(module).withoutSdk()
                .recursively().pathsList.pathList
    }
}