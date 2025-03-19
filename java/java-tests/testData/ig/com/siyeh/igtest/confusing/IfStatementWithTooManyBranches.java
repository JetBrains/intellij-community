package com.siyeh.igtest.confusing;

public class IfStatementWithTooManyBranches
{
    public void foo()
    {
        final int x = barangus();
        if(x == 1){
        } else if(x == 2){
        } else if(x == 3){
        } else if(x == 4){
        } else if(x == 5){
        } else if(x == 6){
        } else if(x == 7){
        } else if(x == 8){
        } else if(x == 9){
        } else if(x == 10){
        } else if(x == 11){
        } 
    }

    private int barangus()
    {
        return 3;
    }
}
