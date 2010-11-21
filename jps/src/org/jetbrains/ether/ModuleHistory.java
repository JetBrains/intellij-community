package org.jetbrains.ether;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 19.11.10
 * Time: 3:40
 * To change this template use File | Settings | File Templates.
 */
public class ModuleHistory implements Serializable {
    String myName;
    long mySourceStamp;
    long myOutputStamp;
    long myTestSourceStamp;
    long myTestOutputStamp;

    public ModuleHistory (String name, long ss, long os, long tss, long tos) {
        myName = name;
        mySourceStamp = ss;
        myOutputStamp = os;
        myTestSourceStamp = tss;
        myTestOutputStamp = tos;
    }

    public boolean isDirty (){
        return (myOutputStamp <= mySourceStamp) || (myTestOutputStamp <= myTestSourceStamp);
    }
}
