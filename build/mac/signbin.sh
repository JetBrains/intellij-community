#!/bin/bash
FILENAME=$1
USERNAME=$2
PASSWORD=$3
CODESIGN_STRING=$4
FILEPATH=$(dirname $0)/$FILENAME

# Make sure *.p12 is imported into local KeyChain
security unlock-keychain -p ${PASSWORD} /Users/${USERNAME}/Library/Keychains/login.keychain

echo "signing ${FILEPATH}"
codesign -v --deep --force -s "${CODESIGN_STRING}" ${FILEPATH}
echo "signing is done"
echo "check sign"
codesign -v ${FILEPATH} -vvvvv
echo "check sign done"
