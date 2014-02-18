import java.util.*;
class IdeaGenericsTest {
    interface Animal<T extends Animal<T>> {
        List<T> getFriends();
    }

    class Dog implements Animal<Dog> {
        public List<Dog> getFriends() {
            return null;
        }
    }

    class Cat implements Animal<Cat> {
        public List<Cat> getFriends() {
            return null;
        }
    }

    void mixAnimals() {
        ArrayList<Dog> dogs = null;
        ArrayList<Cat> cats = null;

        <error descr="Inferred type 'java.util.ArrayList<IdeaGenericsTest.Dog>' for type parameter 'V' is not within its bound; should extend 'java.util.ArrayList<IdeaGenericsTest.Cat>'">makeFriends(cats, dogs)</error>;
    }

    private<T extends Animal<T>, V extends ArrayList<T>> void makeFriends(ArrayList<T> someAnimals, V otherAnimals) {
        someAnimals.add(otherAnimals.get(0));
    }
}