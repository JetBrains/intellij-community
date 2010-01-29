#! /bin/bash
#
# This script updates your IntelliJ IDEA CE installation from the latest compiled classes. This way you can easily
# upgrade your working IDEA to the latest changes.
#
# Before you run the script, ensure you have the following:
# 1. Your project for IntelliJ IDEA CE is fully built (do 'Rebuild Project' if you're not sure)
# 2. WORK_IDEA_HOME points to the directory of IntelliJ IDEA build you want to upgrade
# 3. DEV_IDEA_HOME points to the directory of the project you built at step 1
# 4. You quit IntelliJ IDEA
#

if [ -z "$WORK_IDEA_HOME" ]; then
   echo WORK_IDEA_HOME must be defined and point to build you're updating
   exit
fi

if [ -z "$DEV_IDEA_HOME" ]; then
   echo DEV_IDEA_HOME must be defined and point to source base your're updating from
   exit
fi

echo Updating $WORK_IDEA_HOME from compiled classes in $DEV_IDEA_HOME

rm -rf $WORK_IDEA_HOME/lib
rm -rf $WORK_IDEA_HOME/plugins

ant -f update.xml

cd $DEV_IDEA_HOME
cp -R $DEV_IDEA_HOME/out/deploy/* $WORK_IDEA_HOME

cd $WORK_IDEA_HOME/bin

