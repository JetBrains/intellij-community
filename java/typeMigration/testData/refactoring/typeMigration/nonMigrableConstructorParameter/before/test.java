// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
public class Test {
    
    private static final int FIRST = 0;
    
    private Test(int number){
        
    }
    
    public static Test create(){
       return new Test(FIRST);
    }

}