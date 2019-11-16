// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class Test {
  
  private Long migrationField;
  
  public void foo(){
    Long number = (Long) migrationField;
  }
  
}