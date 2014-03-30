package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// We don't use Java URI due to problem — http://cns-etuat-2.localnet.englishtown.com/school/e12/#school/45383/201/221/382?c=countrycode=cc|culturecode=en-us|partnercode=mkge
// it is illegal URI (fragment before query), but we must support such URI
// Semicolon as parameters separator is supported (WEB-6671)
public interface Url {
  @NotNull
  String getPath();

  boolean isInLocalFileSystem();

  String toDecodedForm();

  @NotNull
  String toExternalForm();

  @Nullable
  String getScheme();

  @Nullable
  String getAuthority();

  @Nullable
  String getParameters();

  boolean equalsIgnoreParameters(@Nullable Url url);

  @NotNull
  Url trimParameters();
}
