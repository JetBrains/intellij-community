// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage.api;

import java.util.Objects;

// 128 bit key
public class Address {
  private final long highBytes;
  private final long lowBytes;

  public Address(long highBytes, long lowBytes) {
    this.highBytes = highBytes;
    this.lowBytes = lowBytes;
  }

  public long getHighBytes() {
    return highBytes;
  }

  public long getLowBytes() {
    return lowBytes;
  }

  public boolean isNovelty() {
    return lowBytes < 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Address address = (Address)o;
    return highBytes == address.highBytes && lowBytes == address.lowBytes;
  }

  @Override
  public int hashCode() {
    // TODO: inline
    return Objects.hash(highBytes, lowBytes);
  }
}
