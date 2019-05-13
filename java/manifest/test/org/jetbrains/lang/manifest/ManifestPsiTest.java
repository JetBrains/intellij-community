/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.lang.manifest;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.psi.Header;
import org.jetbrains.lang.manifest.psi.HeaderValue;
import org.jetbrains.lang.manifest.psi.ManifestFile;

public class ManifestPsiTest extends LightIdeaTestCase {
  public void testFile() {
    ManifestFile file = createFile("");
    assertEquals(0, file.getSections().size());
    assertNull(file.getMainSection());
    assertEquals(0, file.getHeaders().size());

    file = createFile("Header: value\n\nAnother-Header: another value\n");
    assertEquals(2, file.getSections().size());
    assertNotNull(file.getMainSection());
    assertEquals(1, file.getHeaders().size());
    assertNotNull(file.getHeader("Header"));
    assertNull(file.getHeader("Another-Header"));
  }

  public void testHeader() {
    ManifestFile file = createFile("Header: value\nEmpty-Header:\nBad-Header\n");
    assertHeaderValue(file, "Header", "value");
    assertHeaderValue(file, "Empty-Header", "");
    assertHeaderValue(file, "Bad-Header", null);
  }

  private static ManifestFile createFile(String text) {
    PsiFile file = LightPlatformTestCase.createLightFile("MANIFEST.MF", text);
    assert file instanceof ManifestFile : file;
    return (ManifestFile)file;
  }

  private static void assertHeaderValue(ManifestFile file, String name, @Nullable String expected) {
    Header header = file.getHeader(name);
    assertNotNull(header);

    HeaderValue value = header.getHeaderValue();
    if (expected == null) {
      assertNull(value);
    }
    else {
      assertNotNull(value);
      assertEquals(expected, value.getUnwrappedText());
    }
  }
}
