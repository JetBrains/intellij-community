package com.siyeh.igtest.serialization.non_serializable_with_serial_version_uid_field;

import java.io.Serializable;

public class <warning descr="Non-serializable class 'NonSerializableWithSerialVersionUIDField' defines a 'serialVersionUID' field">NonSerializableWithSerialVersionUIDField</warning> {

    private static final long serialVersionUID = -25111423935153899L;

    void foo() {
        new <warning descr="Non-serializable anonymous class derived from 'Object' defines a 'serialVersionUID' field">Object</warning>() {
            private static final long serialVersionUID = -25111423935153899L;
        };
    }
}
@interface <warning descr="Non-serializable @interface 'X' defines a 'serialVersionUID' field">X</warning> {
    long serialVersionUID = -25111423935153899L;
}
interface <warning descr="Non-serializable interface 'Y' defines a 'serialVersionUID' field">Y</warning> {
    long serialVersionUID = -25111423935153899L;
}
