// "Implement methods" "true"
interface Card {
    void play();
}

enum E implements Card {
    A{}, B<caret>
}