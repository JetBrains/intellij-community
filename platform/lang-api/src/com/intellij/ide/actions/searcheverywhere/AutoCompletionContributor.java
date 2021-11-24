// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import java.util.List;

public interface AutoCompletionContributor {

  List<AutoCompletionCommand> getAutocompleteItems(String pattern, int caretPosition);
}
