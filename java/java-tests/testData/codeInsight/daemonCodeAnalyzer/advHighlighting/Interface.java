interface i <error descr="No implements clause allowed for interface">implements Runnable</error> {}

interface ii {}
class cs extends <error descr="No interface expected here">ii</error> {}
interface is extends ii {}

class cs2 implements <error descr="Interface expected here">cs</error> {}
interface is3 extends <error descr="Interface expected here">cs</error> {}

abstract class Exercis {
    public static void main() {
       Object o = <error descr="Class name expected here">java.lang</error>.this;
      
             new Runnable() {
                 public void run() {
                     <error descr="No interface expected here">Runnable</error>.this.run(); ///
                 }
             };
         }
}
