/*
 * Copyright 2001-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.config;

import org.jetbrains.annotations.NotNull;

/**
 * Options for the various policies.
 */
public final class PolicyOptions {

    private static final InsertWhere[] newMethodOptions = {InsertWhere.AT_CARET, InsertWhere.AFTER_EQUALS_AND_HASHCODE, InsertWhere.AT_THE_END_OF_A_CLASS};

    private static final DuplicationPolicy[] conflictOptions = {DuplicationPolicy.ASK, DuplicationPolicy.REPLACE, DuplicationPolicy.DUPLICATE};

    private PolicyOptions() {}

  /**
     * Gets the options for the insert new method policy.
     * @return the options for the insert new method policy.
     */
    public static InsertWhere @NotNull [] getNewMethodOptions() {
        return newMethodOptions;
    }

    /**
     * Gets the options for the conflict resolution policy.
     * @return the options for the conflict resolution policy.
     */
    public static DuplicationPolicy @NotNull [] getConflictOptions() {
        return conflictOptions;
    }
}
