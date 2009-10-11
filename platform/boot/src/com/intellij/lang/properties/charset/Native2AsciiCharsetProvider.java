/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.lang.properties.charset;

import java.nio.charset.spi.CharsetProvider;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Collections;

/**
 * @author Alexey
 */
@SuppressWarnings({"UnusedDeclaration"})
public class Native2AsciiCharsetProvider extends CharsetProvider {
  public Native2AsciiCharsetProvider() {
  }

  public Charset charsetForName(String charsetName) {
    return Native2AsciiCharset.forName(charsetName);
  }

  public Iterator<Charset> charsets() {
    return Collections.<Charset>emptyList().iterator();
  }
}