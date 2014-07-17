/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.*
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectPaths
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.ModuleBuildTarget
import java.io.File
import com.intellij.openapi.util.text.StringUtil
import org.junit.Assert

/**
 * @author nik
 */
public class ModuleClasspathTest(): JpsBuildTestCase() {
    override fun setUp() {
        super.setUp()
        addJdk("1.6", "/jdk.jar")
        addJdk("1.5", "/jdk15.jar")
        loadProject("moduleClasspath/moduleClasspath.ipr")
    }

    private fun getProjectPath(): String {
        return FileUtil.toSystemIndependentName(getTestDataRootPath()) + "/moduleClasspath/moduleClasspath.ipr"
    }

    override fun getTestDataRootPath(): String {
        return FileUtil.toCanonicalPath(PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/output")!!.getAbsolutePath(), '/')!!
    }

    public fun testSimpleClasspath() {
        assertClasspath("util", false, listOf("util/lib/exported.jar", "/jdk15.jar"))
    }

    public fun testScopes() {
        assertClasspath("test-util", false, listOf("/jdk.jar", "test-util/lib/provided.jar"))
        assertClasspath("test-util", true, listOf("/jdk.jar", "test-util/lib/provided.jar", "test-util/lib/test.jar", "out/production/test-util"))
    }

    public fun testDepModules() {
        assertClasspath("main", false, listOf("util/lib/exported.jar", "out/production/util", "/jdk.jar", "main/lib/service.jar"))
        assertClasspath("main", true, listOf("out/production/main", "util/lib/exported.jar", "out/test/util", "out/production/util", "/jdk.jar", "out/test/test-util", "out/production/test-util", "main/lib/service.jar"))
    }

    public fun testCompilationClasspath() {
        val chunk = createChunk("main")
        assertClasspath(listOf("util/lib/exported.jar", "out/production/util", "/jdk.jar"), getPathsList(ProjectPaths.getPlatformCompilationClasspath(chunk, true)))
        assertClasspath(listOf("main/lib/service.jar"), getPathsList(ProjectPaths.getCompilationClasspath(chunk, true)))
    }

    private fun assertClasspath(moduleName: String, includeTests: Boolean, expected: List<String>) {
        val classpath = getPathsList(ProjectPaths.getCompilationClasspathFiles(createChunk(moduleName), includeTests, true, true))
        assertClasspath(expected, toSystemIndependentPaths(classpath))
    }

    private fun createChunk(moduleName: String): ModuleChunk {
        val module = myProject.getModules().firstOrNull { it.getName() == moduleName }
        return ModuleChunk(setOf(ModuleBuildTarget(module!!, JavaModuleBuildTargetType.PRODUCTION)))
    }

    private fun assertClasspath(expected: List<String>, classpath: List<String>) {
        val basePath = FileUtil.toSystemIndependentName(File(getProjectPath()).getParentFile()!!.getAbsolutePath()) + "/"
        val actual = toSystemIndependentPaths(classpath).map { StringUtil.trimStart(it, basePath) }
        Assert.assertEquals(expected.join("\n"), actual.join("\n"))
    }

    private fun toSystemIndependentPaths(classpath: List<String>): List<String> {
        return classpath.map(FileUtil::toSystemIndependentName)
    }

    public fun getPathsList(files: Collection<File>): List<String> {
        return files.map(::getCanonicalPath)
    }
}

private fun getCanonicalPath(file: File): String {
    val path = file.getPath()
    return if (path.contains(".")) FileUtil.toCanonicalPath(path)!! else FileUtil.toSystemIndependentName(path)
}

