class A{
 public int askdh(){
  return 1;
 }
}

class B extends A{
 public void askdh(){
  return 2;
 }
}

class Super1 extends B{
 {
  super.<ref>askdh();
 }
}