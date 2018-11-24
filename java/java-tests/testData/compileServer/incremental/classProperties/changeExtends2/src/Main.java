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
