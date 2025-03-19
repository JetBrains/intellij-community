package com.siyeh.igtest.serialization;

import java.io.Serializable;


public class SerializableInnerClassHasSerialVersionUIDInspection
{
     static class InnerStatic implements Serializable{

     }
      class InnerNonStatic implements Serializable{

     }
}
