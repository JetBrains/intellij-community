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
package com.intellij.codeInspection;

/**
 * Marker interface for inspections which can be executed as part of the "Code Cleanup" action.
 * Such inspections need to provide quick-fixes which can run without the user's input and be generally safe to apply.
 * <p>
 * Inspections marked with this interface must also have <code>cleanupTool="true"</code> in their XML entry.
 */
public interface CleanupLocalInspectionTool {
}
