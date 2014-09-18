/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.progress;

/**
 * Marker interface which means this indicator cancellation behaves in a standard way:
 * - checkCanceled() checks for isCanceled() and throws PCE if returned true
 * - cancel() sets the corresponding flag
 * - isCanceled() is true after cancel() call
 * - all methods above are final
 */
public interface StandardProgressIndicator extends ProgressIndicator {
}
