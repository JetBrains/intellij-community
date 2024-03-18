// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * <h1>Command with arguments and options.</h1>
 * <p>
 * Each {@link com.intellij.commandInterface.command.Command} may have one or more positional {@link com.intellij.commandInterface.command.Argument arguments}
 * and several {@link com.intellij.commandInterface.command.Option options} (with arguments as well).
 * You need to implemenet {@link com.intellij.commandInterface.command.Command} first.
 * </p>
 *
 * @author Ilya.Kazakevich
 */
package com.intellij.commandInterface.command;