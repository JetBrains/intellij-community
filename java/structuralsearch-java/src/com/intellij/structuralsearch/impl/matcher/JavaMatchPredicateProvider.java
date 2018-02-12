/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.impl.matcher.predicates.*;

import java.util.Set;

public class JavaMatchPredicateProvider extends MatchPredicateProvider {
  @Override
  public void collectPredicates(MatchVariableConstraint constraint, String name, MatchOptions options, Set<MatchPredicate> predicates) {
    if (!StringUtil.isEmptyOrSpaces(constraint.getNameOfExprType())) {
      MatchPredicate predicate = new ExprTypePredicate(
        constraint.getNameOfExprType(),
        name,
        constraint.isExprTypeWithinHierarchy(),
        options.isCaseSensitiveMatch(),
        constraint.isPartOfSearchResults()
      );

      if (constraint.isInvertExprType()) {
        predicate = new NotPredicate(predicate);
      }
      predicates.add(predicate);
    }

    if (!StringUtil.isEmptyOrSpaces(constraint.getNameOfFormalArgType())) {
      MatchPredicate predicate = new FormalArgTypePredicate(
        constraint.getNameOfFormalArgType(),
        name,
        constraint.isFormalArgTypeWithinHierarchy(),
        options.isCaseSensitiveMatch(),
        constraint.isPartOfSearchResults()
      );
      if (constraint.isInvertFormalType()) {
        predicate = new NotPredicate(predicate);
      }
      predicates.add(predicate);
    }
  }
}
