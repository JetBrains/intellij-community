#/bin/sh
./getPlugins.sh
set -e
: ${IDEA_HOME?"Need to set IDEA_HOME to point to a valid IntelliJ IDEA installation"}
cd robovm/robovm-idea
mvn -Didea.home="$IDEA_HOME" clean package -Pdeployment
cd ../..
ant -f build-robovm.xml
rm -rf out/robovm-studio
mkdir -p out/robovm-studio
version=$(<build-robovm/build.txt)
cp out/artifacts/*.mac.zip out/robovm-studio/robovm-studio-$version.zip
cd out/robovm-studio
unzip robovm-studio-$version.zip
cd ../..
appdmg robovm/robovm-studio-dmg/dmg.json out/robovm-studio/robovm-studio-$version.dmg
