/**
 * Created with IntelliJ IDEA.
 * User: db
 * Date: 20.02.12
 * Time: 13:19
 * To change this template use File | Settings | File Templates.
 */
public class Main {
    static void doMe (final Puper x) {
        System.out.println("Puper!");
    }

    static void doMe (final Super x){
        System.out.println("Super!");
    }

    public static void main (String[] args){
        doMe(new Victim.SubVictim());
    }
}
