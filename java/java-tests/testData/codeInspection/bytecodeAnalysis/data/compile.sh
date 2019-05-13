#!/bin/sh

# use Java 9+
JAVAC="$(which javac)"

if [ ! -x "$JAVAC" ]; then
  echo "Java compiler not defined"
  exit 1
fi

rm -rf classes
mkdir classes

"$JAVAC" -g --release 8 -d classes src/Expect*.java

"$JAVAC" -g --release 8 -d classes -cp classes src/data/*.java
rm -rf classes/bytecodeAnalysis/data/ExtTestConverterData*

"$JAVAC" -g --release 9 -d classes -cp classes src/java9/*.java

"$JAVAC" -g --release 8 -cp classes conflict/*.java
