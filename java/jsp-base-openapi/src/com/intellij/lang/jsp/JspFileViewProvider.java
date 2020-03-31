// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.jsp;

import java.util.Set;

public interface JspFileViewProvider extends JspxFileViewProvider {
  Set<String> getXmlNsPrefixes(CharSequence buffer);
}
