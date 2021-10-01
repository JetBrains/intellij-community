package org.jetbrains.intellij.build.dependencies

import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import java.nio.file.Path

class BuildDependenciesUtilTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none()

    @Test
    void testEntryFile() {
        Path root = Path.of("/root")
        Assert.assertEquals(
                "/root/xxx",
                BuildDependenciesUtil.entryFile(root, "///////xxx").toString().replace('\\', '/')
        )
        Assert.assertEquals(
                "/root/..xxx",
                BuildDependenciesUtil.entryFile(root, "///////..xxx").toString().replace('\\', '/')
        )
    }

    @Test
    void testEntryFileInvalid() {
        thrown.expect(IOException.class)
        BuildDependenciesUtil.entryFile(Path.of("/root"), "/../xxx")
    }
}
