package com.siyeh.igtest.serialization.serializable_has_serial_version_uid_field;

import java.io.Serializable;

public class SerializableHasSerialVersionUidFieldQf : Serializable {
  companion object {
    private const val serialVersionUID = 2893173730119287781L;
  }
}