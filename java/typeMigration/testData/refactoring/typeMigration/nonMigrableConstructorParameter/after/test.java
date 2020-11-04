// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
public class Test {
    
    private static final long FIRST = 0;
    
    private Test(long number){
        
    }
    
    public static Test create(){
       return new Test(FIRST);
    }

}