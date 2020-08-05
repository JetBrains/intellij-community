record WrittenFields(int x, 
                     int y, 
                     int z) {
  public WrittenFields {
    <error descr="Cannot assign a value to final variable 'x'">this.x</error> = 0;
    if (Math.random() > 0.5) <error descr="Cannot assign a value to final variable 'y'">this.y</error> = 1;
  }
}
