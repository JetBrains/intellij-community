// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

import org.jetbrains.jps.api.GlobalOptions;

/**
 * @author Eugene Zhuravlev
 */
public final class UnprocessedFSChangesNotification extends CustomBuilderMessage{
  public UnprocessedFSChangesNotification() {
    super(
      GlobalOptions.JPS_SYSTEM_BUILDER_ID,
      GlobalOptions.JPS_UNPROCESSED_FS_CHANGES_MESSAGE_ID,
      "Some files were changed during the build. Additional compilation may be required."
    );
  }
}
