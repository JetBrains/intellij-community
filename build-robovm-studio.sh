#/bin/sh
./getPlugins.sh
: ${IDEA_HOME?"Need to set IDEA_HOME to point to a valid IntelliJ IDEA installation"}
cd robovm/robovm-idea
mvn -Didea.home="$IDEA_HOME" clean package -Pdeployment
cd ../..
ant -f build-robovm.xml