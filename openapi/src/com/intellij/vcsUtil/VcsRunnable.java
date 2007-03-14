/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 14.03.2007
 * Time: 14:33:16
 */
package com.intellij.vcsUtil;

import com.intellij.openapi.vcs.VcsException;

public interface VcsRunnable {
  void run() throws VcsException;
}