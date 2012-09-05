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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:01:22 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;

public class CompletionCharFilter extends CharFilter {

  @Override
  public Result acceptChar(char c, final int prefixLength, final Lookup lookup) {
    if (!lookup.isCompletion()) return null;

    if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
    switch(c){
      case '.':
      case ',':
      case ';':
      case '=':
      case ' ':
      case ':':
      case '(':
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;

      default:
        return Result.HIDE_LOOKUP;
    }
  }

}