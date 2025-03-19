// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * <h1>Command-line interface</h1>
 * <h2>What is the purpose of this package?</h2>
 * <p>
 * This package is based on ideas of command-line with command, positional arguments, options and their arguments.
 * Initial idea is taken from <a href="http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02">POSIX</a>
 * and enhanced by <a href="http://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html">GNU</a>.
 * It also supported by Python in <a href="https://docs.python.org/2/library/optparse.html">optparse</a> package.
 * Command-line is something like
 * <pre>my_command positional_argument -s --long-option --another-long-option=arg1 arg2 some_other_arg</pre>
 * and this package helps you to parse command lines, highlight errors, display a console-like interface and execute commands.
 * </p>
 * <h2>What this package consists of?</h2>
 * <p>
 * This package has 2 subpackages:
 *   <ol>
 *     <li>{@link com.intellij.commandInterface.command}  contains classes to describe commands, arguments and options.
 *     It is up to you where to obtain list of available commands, but you should implement {@link com.intellij.commandInterface.command.Command}
 *     first, and create list of it with arguments and options. See package info for more details</li>
 *     <li>{@link com.intellij.commandInterface.commandLine} is language based on
 *     <a href="http://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html">GNU</a> command line representation.
 *     It has PSI, so it parses command lines into tree of PsiElements. But this package <strong>is not only for parsing</strong>:
 *     If you provide list of
 *     {@link com.intellij.commandInterface.command.Command commands} to {@link com.intellij.commandInterface.commandLine.psi.CommandLineFile}
 *      (see {@link com.intellij.commandInterface.commandLine.psi.CommandLineFile#setCommands(java.util.List)}}}), it will inject references
 *      (to provide autocompletion) and activate inspection to validate options and arguments. </li>
 *
 *   </ol>
 * </p>
 * <h2>How to use this package?</h2>
 *   <ol>
 *     <li>Implement {@link com.intellij.commandInterface.command.Command}</li>
 *     <li>Create list of {@link com.intellij.commandInterface.command.Command commands}</li>
 *   </ol>
 *
 * @author Ilya.Kazakevich
 */
package com.intellij.commandInterface;

