git clone git://git.jetbrains.org/idea/android.git android
cd android
git pull
cd ..
git clone git://git.jetbrains.org/idea/adt-tools-base.git android/tools-base
cd android/tools-base
git pull
cd ../..
git clone https://github.com/robovm/robovm-idea robovm/robovm-idea
cd robovm/robovm-idea
git checkout master
git pull
: ${ROBOVM_IDEA_PLUGIN_VERSION="master"}
git checkout $ROBOVM_IDEA_PLUGIN_VERSION
cd ../..
