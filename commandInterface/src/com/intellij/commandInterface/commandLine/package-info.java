// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * <h1>GNU command line language PSI</h1>
 * <h2>Command line language</h2>
 * <p>
 * <a href="http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02">GNU</a> command line language syntax
 * based on <a href="http://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html">POSIX</a> syntax.
 * <pre>
 *   my_command --option --option-2=argument positional_argument -s
 * </pre>
 * </p>
 * <h2>PSI</h2>
 * <p>Generation based on Grammar-Kit, see .bnf file, do not edit parser nor lexer manually.
 * When parsed, {@link com.intellij.commandInterface.commandLine.psi.CommandLineFile} is root element for
 * {@link com.intellij.commandInterface.commandLine.CommandLineLanguage}.
 * <strong>Warning</strong>: always fill {@link com.intellij.commandInterface.commandLine.psi.CommandLineFile#setCommandsAndDefaultExecutor(java.util.List, CommandExecutor)}
 * if possible.
 * </p>
 * <h2>Extension points</h2>
 * <p>This package has a a lot of extension points (language, inspection etc). Make sure all of them are registered</p>
 *
 * @author Ilya.Kazakevich
 */
package com.intellij.commandInterface.commandLine;

import com.intellij.commandInterface.command.CommandExecutor;