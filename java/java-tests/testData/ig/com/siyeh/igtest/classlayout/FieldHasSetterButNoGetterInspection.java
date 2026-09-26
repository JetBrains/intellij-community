package com.siyeh.igtest.classlayout;

public class FieldHasSetterButNoGetterInspection {
    private String stringVar = "";
    private boolean  boolVar  = false;

    public void setStringVar(String stringVar) {
        this.stringVar = stringVar;
    }

    public boolean getBoolVar() {
        return boolVar;
    }

    public void setBoolVar(boolean boolVar) {
        this.boolVar = boolVar;
    }
}
