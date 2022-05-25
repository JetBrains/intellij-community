/*
 Copyright 2020 Thomas Rosenau

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package de.thomasrosenau.diffplugin;

import java.io.File;
import java.io.IOException;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataFile;
import de.thomasrosenau.diffplugin.parser.DiffParserDefinition;
import org.jetbrains.annotations.NotNull;

abstract class DiffParserTestBase extends ParsingTestCase {
    DiffParserTestBase() {
        this("diff");
    }

    DiffParserTestBase(String dataPath) {
        super("", dataPath, new DiffParserDefinition());
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/diffs";
    }

    @Override
    protected String loadFile(@NotNull @TestDataFile String name) throws IOException {
        // Parent method for some reason uses .trim(), which undesirably removes trailing newlines
        return FileUtil.loadFile(new File(myFullDataPath, name), CharsetToolkit.UTF8, true);
    }

}
