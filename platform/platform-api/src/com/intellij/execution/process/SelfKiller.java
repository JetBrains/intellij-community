/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.process;

/**
 * Marker interface that represents a process that kills itself, for example a remote process, that can't be killed by the local OS.
 *
 * The process that can't be killed by local OS should implement this interface for OSProcessHandler to work correctly.
 * The process implementing this interface is supposed to be destroyed with Process.destroy() method.
 *
 * Also implementations of ProcessHandler should take in account that process can implement this interface
 */
public interface SelfKiller {}
