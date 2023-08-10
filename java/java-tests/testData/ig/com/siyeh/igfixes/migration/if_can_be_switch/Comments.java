class Comments {

    String foo(int par) {
         if<caret>(par == 11)
             return "ciao";/* case 1 bla bla */
         else if(par == 14)
             return "fourteen";//case 2 bla bla
         else if(par == 15)
             return "fifteen"; //case 3 chchah
         else
             return "default";

    }
}