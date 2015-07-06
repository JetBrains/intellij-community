#/bin/sh
./getPlugins.sh
set -e
: ${IDEA_HOME?"Need to set IDEA_HOME to point to a valid IntelliJ IDEA installation"}

## Build the RoboVM IDEA plugin
cd robovm/robovm-idea
awk '!/idea-version/' src/main/resources/META-INF/plugin.xml > plugin.xml.tmp && mv plugin.xml.tmp src/main/resources/META-INF/plugin.xml
mvn -Didea.home="$IDEA_HOME" clean package -Pdeployment
git checkout -- src/main/resources/META-INF/plugin.xml
cd ../..

## Apply versioning to IdeaApplicationInfo.xml based on
## version found in robovm-idea/pom.xml. The version
## is defined by the ROBOVM_IDEA_PLUGIN_VERSION environment 
## variable, based on which the respective tag will be
## pulled in
java -cp . Versioning robovm/robovm-idea/pom.xml robovm/robovm-studio-branding/
version=$(java -cp . Versioning robovm/robovm-idea/pom.xml robovm/robovm-studio-branding/src/idea/IdeaApplicationInfo.xml robovm/robovm-studio-dmg/dmg.json)
rm Versioning.class

## Build IntelliJ IDEA using our own build files
ant -f build-robovm.xml
rm -rf out/robovm-studio
mkdir -p out/robovm-studio

## Copy the artifacts and build the DMG
cp out/artifacts/*.mac.zip out/robovm-studio/robovm-studio-$version.zip
cd out/robovm-studio
unzip robovm-studio-$version.zip
cd ../..
appdmg robovm/robovm-studio-dmg/dmg.json out/robovm-studio/robovm-studio-$version.dmg

## Revert the IdeaApplicationInfo.xml and dmg.json file
git checkout -- robovm/robovm-studio-branding/src/idea/IdeaApplicationInfo.xml
git checkout -- robovm/robovm-studio-dmg/dmg.json